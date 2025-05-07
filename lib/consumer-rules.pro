
# keep all iCalendar properties/parameters (referenced over ServiceLoader)
-keep class net.fortuna.ical4j.** { *; }

# don't warn when these are missing
-dontwarn com.github.erosb.jsonsKema.**
-dontwarn groovy.**
-dontwarn java.beans.Transient
-dontwarn javax.cache.**
-dontwarn org.codehaus.groovy.**
-dontwarn org.jparsec.**
