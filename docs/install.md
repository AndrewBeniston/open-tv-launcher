# Install by hand

These are the steps that the prompt in the [README](../README.md) asks an AI assistant to do. You can also do them yourself. If something goes wrong, see [Troubleshooting](troubleshooting.md).

## What you need

| Need | Notes |
| --- | --- |
| An Android TV or Google TV with Android 11 or newer | It was built and tested on a Chromecast with Google TV (Google TV 14) and an Android TV 11 television. |
| A computer with adb, on the same network as the TV | adb is in the Android SDK's platform-tools. |
| A JDK 17 or newer and the Android SDK | To build. You need SDK platform 37 and build tools 36. |
| Stremio on the TV | Required. Titles open in Stremio. |
| A Trakt account | Required and free. Set your country in your Trakt profile: the Netflix button uses it. |
| A Trakt API application | Required and free. You make your own (step 2). |
| YouTube, Netflix, BBC iPlayer | Each one is optional. The launcher hides what the TV does not have. |

## 1. Connect adb to the TV

1. On the TV, turn on the developer options. On most TVs: Settings, About, then select the build number seven times. The menu names differ between TVs.
2. In the developer options, turn on network debugging or Wireless debugging.
3. Connect from the computer. Google TV's Wireless debugging needs a pairing code first:

```sh
adb pair <tv-address>:<pairing-port>     # Google TV, Wireless debugging only
adb connect <tv-address>:<port>          # many Android TVs use port 5555
adb devices                              # the first column is the serial for the steps below
```

## 2. Make a Trakt API application

1. Sign in at trakt.tv, and open your settings, Your API Apps, New Application.
2. Give it a name. Set the redirect URI to `urn:ietf:wg:oauth:2.0:oob`. The launcher uses Trakt's device sign-in, which needs no web page of its own.
3. Save it. Trakt shows the application's Client ID and Client Secret.

Keep the secret private. Each person who builds the launcher uses their own application.

## 3. Set up local.properties

Put these lines in `local.properties` in the repository folder. Git ignores this file. Never commit it, and never post its values.

```properties
sdk.dir=/path/to/android/sdk
TRAKT_CLIENT_ID=your client id
TRAKT_CLIENT_SECRET=your client secret

# Optional
APPLICATION_ID=app.opentvlauncher
WIKIDATA_CONTACT=https://github.com/AndrewBeniston/open-tv-launcher
```

- `APPLICATION_ID` is the id the app installs as. The default is `app.opentvlauncher`. Keep the same id for every update: a new id installs a second app, with its own sign-in and settings. `scripts/app-id.sh` prints the id that the scripts use.
- `WIKIDATA_CONTACT` goes in the user agent of the Netflix id lookup, as Wikimedia asks. The default is this repository's address.
- Without the Trakt id and secret, the build still works, but Trakt sign-in is unavailable.

## 4. Build and install

```sh
scripts/install.sh <adb-serial>
```

The script makes a signing key the first time, builds the release APK and installs it. The key is in `signing/`, which Git ignores. Back up `signing/`: an APK with a different key cannot update the app, so the TV must uninstall it first.

To sideload an APK that you built before:

```sh
./gradlew assembleRelease
adb -s <adb-serial> install -r build/app/outputs/apk/release/app-release.apk
```

The app shows in the TV's apps list as Personal TV Launcher. It does not replace the TV's own home screen.

## 5. Back up the TV's settings

Before you change anything, save the settings that the next steps change: the screensaver, the accessibility services and the Home app.

```sh
scripts/tv-settings.sh backup <adb-serial> tv-settings-before.txt
```

Keep the file. It holds no address or serial. The script never writes over an earlier backup.

## 6. Sign in to Trakt

Open the launcher. It shows a Trakt device code. On a phone, open trakt.tv/activate and enter the code. "Skip for Now" opens Home without the Trakt rows.

## 7. Let the launcher read TV channels

```sh
adb -s <adb-serial> shell pm grant "$(scripts/app-id.sh)" android.permission.READ_TV_LISTINGS
```

Without this, the YouTube row and the Stremio fallback row are hidden.

## 8. The screensaver (optional)

The launcher's Settings, Screensaver, opens the TV's screensaver settings, where you can choose it if the TV lists it. You can also set it with adb:

```sh
adb -s <adb-serial> shell settings put secure screensaver_components "$(scripts/app-id.sh)/app.opentvlauncher.screensaver.ScreensaverService"
```

The class name stays `app.opentvlauncher...` whatever your app id is.

## 9. The Home override (optional)

The Home override brings the launcher back when Google TV Home comes to the front, or after a short press of the Home key. It is off after installation. [The Home override](home-override.md) explains it in full.

```sh
scripts/home-override.sh on <adb-serial>
```

Or turn it on in the TV's Settings, Accessibility, Personal TV Launcher Home. The script adds only this service and keeps every other enabled service.

## Undo

| Part | Undo |
| --- | --- |
| Home override | `scripts/home-override.sh off <adb-serial>`, or Settings, Accessibility, Personal TV Launcher Home, Off. A force-stop of the launcher also turns it off. |
| Every setting from step 5 | `scripts/tv-settings.sh restore <adb-serial> tv-settings-before.txt` |
| The app | `adb -s <adb-serial> uninstall "$(scripts/app-id.sh)"`, or the TV's Settings, Apps |
| Trakt | Remove the application from your Trakt account's connected apps on trakt.tv |

## Limits

- **Never disable Google TV Home** (`com.google.android.apps.tv.launcherx`) on a Chromecast with Google TV. On 24 Oct 2026 this made the Chromecast start its setup recovery screen hundreds of times at boot, and the screen stayed black. The launcher never needs it disabled.
- The launcher is not a Home app. It has no Home intent filter, so the Home key always goes to the TV's own home first. With the override on, the launcher comes back 120 ms later.
- The Home override was tested only with Google TV Home. On a TV with Android TV Home, such as a Philips Android TV, it is untested, and by its code it does nothing there: it watches only for Google TV Home's screen.
- The Netflix button needs Trakt's `watchnow=netflix` filter. Trakt answers for the country in your Trakt profile. It was checked only with a Trakt VIP account. A title also needs its Netflix id on Wikidata, Netflix on the TV, and a place on your watchlist, in your recommendations, or in Continue Watching with at least one play.
- The YouTube row needs the grant in step 7 and YouTube's "Recommended" channel, which the launcher finds by its English name.
- The text is English only, and dates use UK formats.
