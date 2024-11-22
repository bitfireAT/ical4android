/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.content.ContentProviderClient
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentUris
import android.content.OperationApplicationException
import android.net.Uri
import android.os.RemoteException
import android.os.TransactionTooLargeException
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

class BatchOperation(
    private val providerClient: ContentProviderClient,
    private val maxOperationsPerYieldPoint: Int? = null
) {

    companion object {

        /** Maximum number of operations per yield point in task providers that are based on SQLiteContentProvider. */
        const val TASKS_OPERATIONS_PER_YIELD_POINT = 499

    }

    private val logger = Logger.getLogger(javaClass.name)

    private val queue = LinkedList<CpoBuilder>()
    private var results = arrayOfNulls<ContentProviderResult?>(0)


    fun nextBackrefIdx() = queue.size

    fun enqueue(operation: CpoBuilder): BatchOperation {
        queue.add(operation)
        return this
    }

    /**
     * Commits all operations from [queue] and then empties the queue.
     *
     * @return number of affected rows
     *
     * @throws RemoteException on calendar provider errors. In case of [android.os.DeadObjectException],
     * the provider has probably been killed/crashed or the calling process is cached and thus IPC is frozen (Android 14+).
     *
     * @throws CalendarStorageException if
     *
     * - the transaction is too large and can't be split (wrapped [TransactionTooLargeException])
     * - the batch can't be processed (wrapped [OperationApplicationException])
     * - the content provider throws a [RuntimeException] (will be wrapped)
     */
    fun commit(): Int {
        var affected = 0
        if (!queue.isEmpty()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Committing ${queue.size} operations:")
                for ((idx, op) in queue.withIndex())
                    logger.log(Level.FINE, "#$idx: ${op.build()}")
            }

            results = arrayOfNulls(queue.size)
            runBatch(0, queue.size)

            for (result in results.filterNotNull())
                when {
                    result.count != null -> affected += result.count ?: 0
                    result.uri != null -> affected += 1
                }
            logger.fine("… $affected record(s) affected")
        }

        queue.clear()
        return affected
    }

    fun getResult(idx: Int) = results[idx]


    /**
     * Runs a subset of the operations in [queue] using [providerClient] in a transaction.
     * Catches [TransactionTooLargeException] and splits the operations accordingly (if possible).
     *
     * @param start index of first operation which will be run (inclusive)
     * @param end   index of last operation which will be run (exclusive!)
     *
     * @throws RemoteException on calendar provider errors. In case of [android.os.DeadObjectException],
     * the provider has probably been killed/crashed or the calling process is cached and thus IPC is frozen (Android 14+).
     *
     * @throws CalendarStorageException if
     *
     * - the transaction is too large and can't be split (wrapped [TransactionTooLargeException])
     * - the batch can't be processed (wrapped [OperationApplicationException])
     * - the content provider throws a [RuntimeException] (will be wrapped)
     */
    private fun runBatch(start: Int, end: Int) {
        if (end == start)
            return     // nothing to do

        try {
            val ops = toCPO(start, end)
            val partResults = providerClient.applyBatch(ops)

            val n = end - start
            if (partResults.size != n)
                logger.warning("Batch operation returned only ${partResults.size} instead of $n results")

            System.arraycopy(partResults, 0, results, start, partResults.size)

        } catch (e: OperationApplicationException) {
            throw CalendarStorageException("Couldn't apply batch operation", e)

        } catch (e: RuntimeException) {
            throw CalendarStorageException("Content provider threw a runtime exception", e)

        } catch(e: TransactionTooLargeException) {
            if (end <= start + 1)
                // only one operation, can't be split
                throw CalendarStorageException("Can't transfer data to content provider (too large data row can't be split)", e)

            logger.warning("Transaction too large, splitting (losing atomicity)")
            val mid = start + (end - start)/2

            runBatch(start, mid)
            runBatch(mid, end)
        }
    }

    private fun toCPO(start: Int, end: Int): ArrayList<ContentProviderOperation> {
        val cpo = ArrayList<ContentProviderOperation>(end - start)

        /* Fix back references:
         * 1. If a back reference points to a row between start and end,
         *    adapt the reference.
         * 2. If a back reference points to a row outside of start/end,
         *    replace it by the actual result, which has already been calculated. */

        var currentIdx = 0
        for (cpoBuilder in queue.subList(start, end)) {
            for ((backrefKey, backref) in cpoBuilder.valueBackrefs) {
                val originalIdx = backref.originalIndex
                if (originalIdx < start) {
                    // back reference is outside of the current batch, get result from previous execution ...
                    val resultUri = results[originalIdx]?.uri ?: throw CalendarStorageException("Referenced operation didn't produce a valid result")
                    val resultId = ContentUris.parseId(resultUri)
                    // ... and use result directly instead of using a back reference
                    cpoBuilder  .removeValueBackReference(backrefKey)
                                .withValue(backrefKey, resultId)
                } else
                    // back reference is in current batch, shift index
                    backref.setIndex(originalIdx - start)
            }

            // Set a possible yield point every MAX_OPERATIONS_PER_YIELD_POINT operations for SQLiteContentProvider
            currentIdx += 1
            if (maxOperationsPerYieldPoint != null && currentIdx.mod(maxOperationsPerYieldPoint) == 0)
                cpoBuilder.withYieldAllowed()

            cpo += cpoBuilder.build()
        }
        return cpo
    }


    class BackReference(
        /** index of the referenced row in the original, non-splitted transaction */
        val originalIndex: Int
    ) {
        /** overridden index, i.e. index within the splitted transaction */
        private var index: Int? = null

        /**
         * Sets the index to use in the splitted transaction.
         * @param newIndex index to be used instead of [originalIndex]
         */
        fun setIndex(newIndex: Int) {
            index = newIndex
        }

        /**
         * Gets the index to use in the splitted transaction.
         * @return [index] if it has been set, [originalIndex] otherwise
         */
        fun getIndex() = index ?: originalIndex
    }


    /**
     * Wrapper for [ContentProviderOperation.Builder] that allows to reset previously-set
     * value back references.
     */
    class CpoBuilder private constructor(
        val uri: Uri,
        val type: Type
    ) {

        enum class Type { INSERT, UPDATE, DELETE }

        companion object {

            fun newInsert(uri: Uri) = CpoBuilder(uri, Type.INSERT)
            fun newUpdate(uri: Uri) = CpoBuilder(uri, Type.UPDATE)
            fun newDelete(uri: Uri) = CpoBuilder(uri, Type.DELETE)

        }


        private var selection: String? = null
        private var selectionArguments: Array<String>? = null

        internal val values = mutableMapOf<String, Any?>()
        internal val valueBackrefs = mutableMapOf<String, BackReference>()

        private var yieldAllowed = false


        fun withSelection(select: String, args: Array<String>): CpoBuilder {
            selection = select
            selectionArguments = args
            return this
        }

        fun withValueBackReference(key: String, index: Int): CpoBuilder {
            valueBackrefs[key] = BackReference(index)
            return this
        }

        fun removeValueBackReference(key: String): CpoBuilder {
            if (valueBackrefs.remove(key) == null)
                throw IllegalArgumentException("$key was not set as value back reference")
            return this
        }

        fun withValue(key: String, value: Any?): CpoBuilder {
            values[key] = value
            return this
        }

        fun withYieldAllowed() {
            yieldAllowed = true
        }


        fun build(): ContentProviderOperation {
            val builder = when (type) {
                Type.INSERT -> ContentProviderOperation.newInsert(uri)
                Type.UPDATE -> ContentProviderOperation.newUpdate(uri)
                Type.DELETE -> ContentProviderOperation.newDelete(uri)
            }

            if (selection != null)
                builder.withSelection(selection, selectionArguments)

            for ((key, value) in values)
                builder.withValue(key, value)
            for ((key, backref) in valueBackrefs)
                builder.withValueBackReference(key, backref.getIndex())

            if (yieldAllowed)
                builder.withYieldAllowed(true)

            return builder.build()
        }

    }

}