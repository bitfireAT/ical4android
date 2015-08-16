package at.bitfire.ical4android;

import android.accounts.Account;
import android.content.ContentProviderClient;

public interface AndroidCalendarFactory {

    AndroidCalendar newInstance(Account account, ContentProviderClient provider, long id);
    AndroidCalendar[] newArray(int size);

}
