/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.ical4android;

import android.util.Log;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Completed;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.Geo;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.PercentComplete;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Sequence;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.Version;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import lombok.Cleanup;
import lombok.Getter;
import lombok.NonNull;

public class Task extends iCalendar {
	private final static String TAG = "ical4android.Task";

	public Long createdAt, lastModified;

    public String summary, location, description, url;
    public Organizer organizer;
    public Geo geoPosition;
    public int priority;
    public Clazz classification;
    public Status status;

    public DtStart dtStart;
    public Due due;
    public Duration duration;
    public Completed completedAt;
    public Integer percentComplete;

    public RRule rRule;
    @Getter private List<RDate> rDates = new LinkedList<>();
    @Getter private List<ExDate> exDates = new LinkedList<>();


    /**
     * Parses an InputStream that contains iCalendar VTODOs.
     *
     * @param stream  input stream containing the VTODOs
     * @param charset charset of the input stream or null (will assume UTF-8)
     * @return array of filled Task data objects (may have size 0) – doesn't return null
     * @throws IOException
     * @throws InvalidCalendarException on parser exceptions
     */
	public static Task[] fromStream(@NonNull InputStream stream, Charset charset) throws IOException, InvalidCalendarException {
        final Calendar ical;
        try {
            if (charset != null) {
                @Cleanup InputStreamReader reader = new InputStreamReader(stream, charset);
                ical = calendarBuilder.build(reader);
            } else
                ical = calendarBuilder.build(stream);
        } catch (ParserException e) {
            throw new InvalidCalendarException("Couldn't parse calendar resource", e);
        }

        ComponentList todos = ical.getComponents(Component.VTODO);
        Task[] tasks = new Task[todos.size()];
        for (int i = todos.size() - 1; i >= 0; i--)
            tasks[i] = fromVToDo((VToDo)todos.get(i));
        return tasks;
    }


    protected static Task fromVToDo(VToDo todo) throws InvalidCalendarException {
        final Task t = new Task();

		if (todo.getUid() != null)
			t.uid = todo.getUid().getValue();
		else {
			Log.w(TAG, "Received VTODO without UID, generating new one");
			t.generateUID();
		}
        if (todo.getSequence() != null)
            t.sequence = todo.getSequence().getSequenceNo();

		if (todo.getCreated() != null)
			t.createdAt = todo.getCreated().getDateTime().getTime();
		if (todo.getLastModified() != null)
			t.lastModified = todo.getLastModified().getDateTime().getTime();

		if (todo.getSummary() != null)
			t.summary = todo.getSummary().getValue();
		if (todo.getLocation() != null)
			t.location = todo.getLocation().getValue();
        if (todo.getGeographicPos() != null)
            t.geoPosition = todo.getGeographicPos();
		if (todo.getDescription() != null)
            t.description = todo.getDescription().getValue();
		if (todo.getUrl() != null)
            t.url = todo.getUrl().getValue();
        if (todo.getOrganizer() != null)
            t.organizer = todo.getOrganizer();

        t.priority = (todo.getPriority() != null) ? todo.getPriority().getLevel() : 0;
		if (todo.getClassification() != null)
            t.classification = todo.getClassification();
		if (todo.getStatus() != null)
            t.status = todo.getStatus();

		if (todo.getDue() != null) {
            t.due = todo.getDue();
			validateTimeZone(t.due);
		}
		if (todo.getDuration() != null)
            t.duration = todo.getDuration();
		if (todo.getStartDate() != null) {
            t.dtStart = todo.getStartDate();
			validateTimeZone(t.dtStart);
		}
		if (todo.getDateCompleted() != null) {
            t.completedAt = todo.getDateCompleted();
			validateTimeZone(t.completedAt);
		}
		if (todo.getPercentComplete() != null)
            t.percentComplete = todo.getPercentComplete().getPercentage();

        t.rRule = (RRule)todo.getProperty(Property.RRULE);
        for (RDate rdate : (List<RDate>) (List<?>) todo.getProperties(Property.RDATE))
            t.rDates.add(rdate);
        for (ExDate exdate : (List<ExDate>) (List<?>) todo.getProperties(Property.EXDATE))
            t.exDates.add(exdate);

        return t;
	}


	public ByteArrayOutputStream toStream() throws IOException {
		final net.fortuna.ical4j.model.Calendar ical = new net.fortuna.ical4j.model.Calendar();
		ical.getProperties().add(Version.VERSION_2_0);
		ical.getProperties().add(prodId);

		final VToDo todo = new VToDo();
		ical.getComponents().add(todo);
		final PropertyList props = todo.getProperties();

		if (uid != null)
			props.add(new Uid(uid));
        if (sequence != 0)
            props.add(new Sequence(sequence));

		if (createdAt != null)
			props.add(new Created(new DateTime(createdAt)));
		if (lastModified != null)
			props.add(new LastModified(new DateTime(lastModified)));

		if (summary != null)
			props.add(new Summary(summary));
		if (location != null)
			props.add(new Location(location));
        if (geoPosition != null)
            props.add(geoPosition);
		if (description != null)
			props.add(new Description(description));
		if (url != null)
			try {
				props.add(new Url(new URI(url)));
			} catch (URISyntaxException e) {
				Log.e(TAG, "Ignoring invalid task URL: " + url, e);
			}
        if (organizer != null)
            props.add(organizer);

		if (priority != 0)
			props.add(new Priority(priority));
		if (classification != null)
			props.add(classification);
		if (status != null)
			props.add(status);

        if (rRule != null)
            props.add(rRule);
        for (RDate rDate : rDates)
            props.add(rDate);
        for (ExDate exDate : exDates)
            props.add(exDate);

		// remember used time zones
		Set<TimeZone> usedTimeZones = new HashSet<>();
		if (due != null) {
			props.add(due);
			if (due.getTimeZone() != null)
				usedTimeZones.add(due.getTimeZone());
		}
		if (duration != null)
			props.add(duration);
		if (dtStart != null) {
			props.add(dtStart);
			if (dtStart.getTimeZone() != null)
				usedTimeZones.add(dtStart.getTimeZone());
		}
		if (completedAt != null) {
			props.add(completedAt);
			if (completedAt.getTimeZone() != null)
				usedTimeZones.add(completedAt.getTimeZone());
		}
		if (percentComplete != null)
			props.add(new PercentComplete(percentComplete));

		// add VTIMEZONE components
		for (TimeZone timeZone : usedTimeZones)
			ical.getComponents().add(timeZone.getVTimeZone());

		CalendarOutputter output = new CalendarOutputter(false);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			output.output(ical, os);
		} catch (ValidationException e) {
			Log.e(TAG, "Couldn't generate valid VTODO", e);
		}
		return os;
	}


    public boolean isAllDay() {
        return  (dtStart != null && !(dtStart.getDate() instanceof DateTime)) ||
                (due != null && !(due.getDate() instanceof DateTime));
    }

    public java.util.TimeZone getTimeZone() {
        java.util.TimeZone tz = null;
        if (dtStart != null && dtStart.getTimeZone() != null)
            tz = dtStart.getTimeZone();
        if (tz == null && due != null)
            tz = due.getTimeZone();

        // fallback
        if (tz == null)
            tz = TimeZone.getDefault();

        return tz;
    }

}
