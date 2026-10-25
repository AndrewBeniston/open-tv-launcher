# The Home override

## What it does

Google TV Home stays enabled and stays the TV's Home app. An accessibility service in the launcher, Open TV Launcher Home, brings the launcher back over it: when Google's home screen comes to the front, or after a short press of the Home key, it starts the launcher 120 ms later. The Home key still goes on to Android. Google's home reports its window late, so a window that comes just after a start only makes the service check again 1.5 s later. Google TV Home's other screens, such as voice search results, stay. The home app's tile in All Apps opens it on purpose, and it stays until another app shows or Home is pressed.

The service starts the launcher with an explicit intent, never a Home intent, so the remote's YouTube and Netflix buttons keep working. Each start waits 120 ms, and any other screen that comes to the front in that time, such as Netflix, a cast or Settings, cancels it. It runs in its own process. It counts automatic starts until another app comes to the front, or the launcher stays in front for 2 s without a break. A fifth in 10 s with neither is a loop between the launcher and Google's home, and it turns itself off. Home key presses never count and never clear the count. When the service connects, it starts the launcher only if the active window is known to be Google's home screen.

## Turn it on

The override is off after installation. Back up the TV's settings first ([Install by hand](install.md), step 5). Then:

```sh
scripts/home-override.sh on <adb-serial>
```

Or turn it on in the TV's Settings, Accessibility, Open TV Launcher Home. The script adds only this service and keeps every other enabled service. It reads the app id from `scripts/app-id.sh`.

## Turn it off

```sh
scripts/home-override.sh off <adb-serial>
```

Or open the TV's Settings, Accessibility, Open TV Launcher Home, and turn it off. A force-stop of the launcher also turns it off. `scripts/tv-settings.sh restore` puts back every setting from the backup.

## Limits

- **Never disable Google TV Home** (`com.google.android.apps.tv.launcherx`) on a Chromecast with Google TV. On 24 Oct 2026 this made the Chromecast start its setup recovery screen hundreds of times at boot, and the screen stayed black. The launcher never needs it disabled.
- The launcher is not a Home app. It has no Home intent filter, so the Home key always goes to the TV's own home first. With the override on, the launcher comes back 120 ms later.
- The override was tested only with Google TV Home. On a TV with Android TV Home, such as a Philips Android TV, it is untested, and by its code it does nothing there: it watches only for Google TV Home's screen.
