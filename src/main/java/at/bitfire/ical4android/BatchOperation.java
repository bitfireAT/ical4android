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
import android.content.OperationApplicationException;
import android.os.RemoteException;

import java.util.ArrayList;

import lombok.NonNull;

public class BatchOperation {

	private final ContentProviderClient providerClient;
	private final ArrayList<ContentProviderOperation> queue = new ArrayList<>();
	ContentProviderResult[] results;


	public BatchOperation(@NonNull ContentProviderClient providerClient) {
		this.providerClient = providerClient;
	}

	public int nextBackrefIdx() {
		return queue.size();
	}

	public void enqueue(ContentProviderOperation operation) {
		queue.add(operation);
	}

	public int commit() throws CalendarStorageException {
		int affected = 0;
		if (!queue.isEmpty())
			try {
				Constants.log.fine("Committing " + queue.size() + " operations …");
				results = providerClient.applyBatch(queue);
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
}
