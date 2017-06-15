/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 */

package at.bitfire.ical4android

import android.content.*
import android.os.RemoteException
import android.os.TransactionTooLargeException
import java.util.*

class BatchOperation(
        private val providerClient: ContentProviderClient
) {

    private val queue = LinkedList<Operation>()
    private lateinit var results: Array<ContentProviderResult?>


    fun nextBackrefIdx()= queue.size

    fun enqueue(operation: Operation) = queue.add(operation)

    @Throws(CalendarStorageException::class)
    fun commit(): Int {
        var affected = 0
        if (!queue.isEmpty())
            try {
                Constants.log.fine("Committing ${queue.size} operations …")

                results = Array<ContentProviderResult?>(queue.size, { null })
                runBatch(0, queue.size)

                for (result in results.filterNotNull())
                    when {
                        result.count != null -> affected += result.count
                        result.uri != null -> affected += 1
                    }
                Constants.log.fine("… $affected record(s) affected")

            } catch(e: Exception) {
                throw CalendarStorageException("Couldn't apply batch operation", e)
            }

        queue.clear()
        return affected
    }

    fun getResult(idx: Int) = results[idx]


    /**
     * Runs a subset of the operations in {@link #queue} using {@link #providerClient} in a transaction.
     * Catches {@link TransactionTooLargeException} and splits the operations accordingly.
     * @param start    index of first operation which will be run (inclusive)
     * @param end      index of last operation which will be run (exclusive!)
     * @throws RemoteException  if the provider clients throws a {@link RemoteException}, or
     *                          if the transaction is too large and can't be split
     * @throws OperationApplicationException
     * @throws CalendarStorageException
     */
    @Throws(RemoteException::class, OperationApplicationException::class, CalendarStorageException::class)
    private fun runBatch(start: Int, end: Int) {
        if (end == start)
            return     // nothing to do

        try {
            Constants.log.fine("Running operations $start to ${end-1}")
            val partResults = providerClient.applyBatch(toCPO(start, end))

            val n = end - start
            if (partResults.size != n)
                throw CalendarStorageException("Batch operation failed partially (only ${partResults.size} of $n operations done)")

            System.arraycopy(partResults, 0, results, start, n)
        } catch(e: TransactionTooLargeException) {
            if (end <= start + 1)
                // only one operation, can't be split
                throw RemoteException("Can't transfer data to content provider (data row too large)")

            Constants.log.warning("Transaction too large, splitting (losing atomicity)")
            val mid = start + (end - start)/2
            runBatch(start, mid)
            runBatch(mid, end)
        }
    }

    fun toCPO(start: Int, end: Int): ArrayList<ContentProviderOperation> {
        val cpo = ArrayList<ContentProviderOperation>(end - start)
        for (op in queue.subList(start, end)) {
            val builder = op.builder
            op.backrefKey?.let { key ->
                if (op.backrefIdx < start) {
                    // back reference is outside of the current batch
                    results[op.backrefIdx]?.let { result ->
                        builder.withValueBackReferences(null)
                                .withValue(key, ContentUris.parseId(result.uri))
                    }
                } else
                    // back reference is in current batch, apply offset
                    builder.withValueBackReference(key, op.backrefIdx - start)
            }
            cpo.add(builder.build())
        }
        return cpo
    }


    class Operation @JvmOverloads constructor(
            val builder: ContentProviderOperation.Builder,
            val backrefKey: String? = null,
            val backrefIdx: Int = -1
    )

}
