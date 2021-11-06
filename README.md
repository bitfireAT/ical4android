
[![Development tests](https://github.com/bitfireAT/ical4android/actions/workflows/test-dev.yml/badge.svg)](https://github.com/bitfireAT/ical4android/actions/workflows/test-dev.yml)
[![Documentation](https://img.shields.io/badge/documentation-kdoc-brightgreen)](https://bitfireat.github.io/ical4android/)


# ical4android

ical4android is a library for Android that brings together iCalendar and Android.
It's a framework for

* parsing and generating iCalendar resources (using [ical4j](https://github.com/ical4j/ical4j))
  from/into data classes that are compatible with the Android Calendar Provider and
  third-party task providers,
* accessing the Android Calendar Provider (and third-party task providers) over a unified API.

It has been primarily developed for:

* [DAVx⁵](https://www.davx5.com)
* [ICSx⁵](https://icsx5.bitfire.at)

and is currently used as git submodule.

Generated KDoc: https://bitfireat.github.io/ical4android/

For questions, suggestions etc. use [Github discussions](https://github.com/bitfireAT/ical4android/discussions).
We're happy about contributions! In case of bigger changes, please let us know in the discussions before.
Then make the changes in your own repository and send a pull request.

_This software is not affiliated to, nor has it been authorized, sponsored or otherwise approved
by Google LLC. Android is a trademark of Google LLC._


## How to use

You can use ical4android as a git submodule or using [jitpack.io](https://jitpack.io/#bitfireAT/ical4android):

    allprojects {
        repositories {
            maven { url 'https://jitpack.io' }
        }
    }
    dependencies {
        implementation 'com.github.bitfireAT:ical4android:<version>'        // see tags for latest version, like 1.0, or use the latest commit ID from main branch
        //implementation 'com.github.bitfireAT:ical4android:main-SNAPSHOT'  // use it only for testing because it doesn't generate reproducible builds
    }


## Contact

```
bitfire web engineering GmbH
Florastraße 27
2540 Bad Vöslau, AUSTRIA
```


## License 

Copyright (C) Ricki Hirner and [contributors](https://github.com/bitfireAT/ical4android/graphs/contributors).

This program comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome
to redistribute it under the conditions of the [GNU GPL v3](https://www.gnu.org/licenses/gpl-3.0.html).

