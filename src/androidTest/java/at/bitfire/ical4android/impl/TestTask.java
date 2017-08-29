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
