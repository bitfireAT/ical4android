/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android;

import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

public class BatchOperation {
	private static final String TAG = "davdroid.BatchOperation";

	private final ContentProviderClient providerClient;
	private final ArrayList<ContentProviderOperation> queue = new ArrayList<>();
	ContentProviderResult[] results;


	public BatchOperation(ContentProviderClient providerClient) {
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
				Log.d(TAG, "Committing " + queue.size() + " operations …");
				results = providerClient.applyBatch(queue);
				for (ContentProviderResult result : results)
					if (result != null)                 // will have either .uri or .count set
						if (result.count != null)
							affected += result.count;
						else if (result.uri != null)
							affected += 1;
				Log.d(TAG, "… " + affected + " record(s) affected");
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
