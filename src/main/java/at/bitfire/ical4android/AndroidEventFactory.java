package at.bitfire.ical4android;

public interface AndroidEventFactory {

    AndroidEvent newInstance(AndroidCalendar calendar, long id);
    AndroidEvent newInstance(AndroidCalendar calendar, Event event);

    AndroidEvent[] newArray(int size);

}
