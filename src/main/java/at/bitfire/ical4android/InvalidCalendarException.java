package at.bitfire.ical4android;

public class InvalidCalendarException extends Exception {

    public InvalidCalendarException(String message) {
        super(message);
    }

    public InvalidCalendarException(String message, Throwable ex) {
        super(message, ex);
    }

}
