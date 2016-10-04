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

package at.bitfire.ical4android;

import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.os.TransactionTooLargeException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import lombok.NonNull;

public class BatchOperation {

    private final ContentProviderClient providerClient;
    private final List<Operation> queue = new LinkedList<>();
    private ContentProviderResult[] results;


    public BatchOperation(@NonNull ContentProviderClient providerClient) {
        this.providerClient = providerClient;
    }

    public int nextBackrefIdx() {
        return queue.size();
    }

    public void enqueue(Operation operation) {
        queue.add(operation);
    }

    public int commit() throws CalendarStorageException {
        int affected = 0;
        if (!queue.isEmpty())
            try {
                Constants.log.fine("Committing " + queue.size() + " operations …");

                results = new ContentProviderResult[queue.size()];
                runBatch(0, queue.size());

                for (ContentProviderResult result : results)
                    if (result != null)                 // will have either .uri or .count set
                        if (result.count != null)
                            affected += result.count;
                        else if (result.uri != null)
                            affected += 1;
                Constants.log.fine("… " + affected + " record(s) affected");

            } catch(OperationApplicationException|RemoteException e) {
                throw new CalendarStorageException("Couldn't apply batch operation", e);
            }

        queue.clear();

        return affected;
    }

    public ContentProviderResult getResult(int idx) {
        return results[idx];
    }


    private void runBatch(int start, int end) throws RemoteException, OperationApplicationException, CalendarStorageException {
        try {
            Constants.log.fine("Running operations " + start + " to " + (end - 1));
            ContentProviderResult partResults[] = providerClient.applyBatch(toCPO(start, end));

            int n = end - start;
            if (partResults.length != n)
                throw new CalendarStorageException("Batch operation failed partially (only " + partResults.length + " of " + n + " operations done)");

            System.arraycopy(partResults, 0, results, start, n);
        } catch(TransactionTooLargeException e) {
            Constants.log.warning("Transaction too large, splitting (losing atomicity)");
            int mid = start + (end - start)/2;
            runBatch(start, mid);
            runBatch(mid, end);
        }
    }

    private ArrayList<ContentProviderOperation> toCPO(int start, int end) {
        ArrayList<ContentProviderOperation> cpo = new ArrayList<>(end - start);
        for (Operation op : queue.subList(start, end)) {
            ContentProviderOperation.Builder builder = op.builder;
            if (op.backrefKey != null) {
                if (op.backrefIdx < start) {
                    // back reference is outside of the current batch
                    builder .withValueBackReferences(null)
                            .withValue(op.backrefKey, ContentUris.parseId(results[op.backrefIdx].uri));
                } else
                    // back reference is in current batch, apply offset
                    builder.withValueBackReference(op.backrefKey, op.backrefIdx - start);
            }
            cpo.add(builder.build());
        }
        return cpo;
    }


    public static class Operation {

        final ContentProviderOperation.Builder builder;
        final String backrefKey;
        final int backrefIdx;

        public Operation(ContentProviderOperation.Builder builder) {
            this.builder = builder;
            backrefKey = null;
            backrefIdx = -1;
        }

        public Operation(ContentProviderOperation.Builder builder, String backrefKey, int backrefIdx) {
            this.builder = builder;
            this.backrefKey = backrefKey;
            this.backrefIdx = backrefIdx;
        }

    }

}
