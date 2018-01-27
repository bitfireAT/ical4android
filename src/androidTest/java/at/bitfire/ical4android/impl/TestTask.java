/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android.impl;

import android.content.ContentValues;

import at.bitfire.ical4android.AndroidTask;
import at.bitfire.ical4android.AndroidTaskFactory;
import at.bitfire.ical4android.AndroidTaskList;
import at.bitfire.ical4android.Task;

public class TestTask extends AndroidTask {

    public TestTask(AndroidTaskList calendar, long id) {
        super(calendar, id);
    }

    public TestTask(AndroidTaskList calendar, Task task) {
        super(calendar, task);
    }


    public static class Factory implements AndroidTaskFactory<TestTask> {

        public static final Factory FACTORY = new Factory();

        @Override
        public TestTask newInstance(AndroidTaskList taskList, long id, ContentValues baseInfo) {
            return new TestTask(taskList, id);
        }

        @Override
        public TestTask newInstance(AndroidTaskList taskList, Task task) {
            return new TestTask(taskList, task);
        }

    }

}
