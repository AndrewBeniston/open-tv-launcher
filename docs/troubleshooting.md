# Troubleshooting

## adb cannot connect to the TV

- Google TV's Wireless debugging needs pairing first, with `adb pair` and the pairing code. The pairing port and the connect port are different, and they can change. Read both from the TV's Wireless debugging screen each time.
- Wireless debugging turns off when the TV restarts. Turn it on again in the developer options.
- The computer and the TV must be on the same network.
- `adb devices` lists the serial to use with the scripts.

## The sign-in screen says Trakt sign-in is not configured

The build has no Trakt API app. Put `TRAKT_CLIENT_ID` and `TRAKT_CLIENT_SECRET` in `local.properties`, then build and install again. See [Install by hand](install.md), steps 2 and 3.

## The install fails with a signature error

The APK has a different signing key from the app on the TV. Build with the key that you backed up in `signing/`. If that key is lost, uninstall the app from the TV first. The TV then loses the app's sign-in and settings.

## A second app appeared on the TV

`APPLICATION_ID` in `local.properties` changed. A new id installs a separate app, with its own sign-in and settings. Set the id back to the old one, and uninstall the extra app with `adb uninstall <its id>`.

## A Stremio button or the Stremio tile is missing

The launcher found no way to open Stremio on the TV. Install Stremio, then open the launcher again. Without Stremio, OK on a title opens Details, and Settings offers only Show Details.

## The YouTube row is missing

- Grant `READ_TV_LISTINGS` ([Install by hand](install.md), step 7).
- The launcher finds YouTube's channel by its English name, "Recommended". On a TV in another language, the row can stay hidden.

## A title has no Netflix button

The Netflix button needs all of these:

- Netflix on the TV.
- The title on Netflix in the country in your Trakt profile. Trakt's `watchnow=netflix` filter says which titles are. It was checked only with a Trakt VIP account.
- The title's Netflix id on Wikidata.
- The title on your watchlist, in your recommendations, or in Continue Watching with at least one play.

## The screensaver is not in the TV's screensaver list

Set it with adb ([Install by hand](install.md), step 8). The class name is always `app.opentvlauncher.screensaver.ScreensaverService`, whatever your app id is.

## The Home key does not bring the launcher back

- The Home override is off after installation, and a force-stop of the launcher turns it off. Turn it on again ([The Home override](home-override.md)).
- The override turns itself off when it sees a loop between the launcher and Google's home: a fifth automatic start in 10 seconds.
- On a TV with Android TV Home instead of Google TV Home, the override is untested and does nothing.

## Undo everything

```sh
scripts/home-override.sh off <adb-serial>
scripts/tv-settings.sh restore <adb-serial> tv-settings-before.txt
adb -s <adb-serial> uninstall "$(scripts/app-id.sh)"
```

Never disable or uninstall the TV's own launcher or setup apps to undo the launcher. On a Chromecast with Google TV, disabling Google TV Home left a black screen.
