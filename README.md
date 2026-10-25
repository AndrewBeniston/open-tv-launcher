# Open TV Launcher

Personal TV Launcher is a home screen for Android TV and Google TV. It uses Kotlin and Compose for TV. It runs on the TV and needs no separate server.

Home shows your Trakt lists (Continue Watching, Top Picks for You, From Your Watchlist), YouTube's recommendations, and buttons that open a title in Stremio or Netflix. It also has Details, All Apps, Settings and a screensaver.

## Requirements

> [!IMPORTANT]
> **Minimum:** Stremio installed on the TV, and a Trakt account with your own Trakt API app. Both are free. Without them, the launcher is not useful.
>
> You also need:
>
> - An Android TV or Google TV device with Android 11 or newer.
> - A computer on the same network as the TV.
> - adb, with Wireless debugging or USB debugging turned on in the TV's developer options.

## Give this prompt to your AI

Paste this prompt into an AI coding assistant on your computer, such as Claude Code or Codex. It sets up the launcher with you, step by step, and asks before it changes anything.

```text
Help me install Open TV Launcher on my Android TV or Google TV.
The repository is https://github.com/AndrewBeniston/open-tv-launcher.
Read its README first. The "Install on your own Android TV" section has
the details, and the scripts folder does most of the work. Adapt each
step to my computer and my TV. Tell me what you will do before you do it.

Goals, in order:
1. Check the minimum with me: Stremio is installed on the TV, and I have
   a Trakt account. If either is missing, stop and tell me what to do.
2. Find out my operating system. Check for a JDK 17 or newer, the Android
   SDK (command-line tools or Android Studio) and adb. Ask me before you
   install anything.
3. Clone the repository.
4. Help me make a free Trakt API app in my browser on trakt.tv: any name,
   and the redirect URI urn:ietf:wg:oauth:2.0:oob. Then write
   local.properties with sdk.dir, TRAKT_CLIENT_ID, TRAKT_CLIENT_SECRET,
   APPLICATION_ID (keep the default, app.opentvlauncher, unless I want
   another) and WIKIDATA_CONTACT (optional). Never commit local.properties,
   and never print the client secret.
5. Make a signing key with scripts/make-keystore.sh, and build the release
   APK. Tell me to back up the signing folder.
6. Connect to the TV with adb. Help me turn on the developer options and
   Wireless debugging or USB debugging. Explain that Google TV needs
   pairing first, that the pairing port and the connect port differ and
   can change, and that Wireless debugging turns off when the TV restarts.
7. Before you change any TV setting, run scripts/tv-settings.sh backup,
   and keep the file.
8. Install the APK, grant READ_TV_LISTINGS, and walk me through the Trakt
   sign-in on the TV.
9. Offer the optional steps one at a time, and wait for my OK: the
   screensaver, then the Home override. Say what each one does and how to
   undo it.
10. Check that the launcher opens, the rows load, and the Home key works as
    the README says. Then tell me what you did, and how to undo each part.

Safety rules:
- Never disable, uninstall or clear the data of the TV's own launcher or
  setup apps. On Google TV, never disable
  com.google.android.apps.tv.launcherx or
  com.google.android.tungsten.setupwraith: the TV can be left with a
  black screen.
- Ask me before every change on the TV.
- Keep the undo commands ready: scripts/home-override.sh off,
  scripts/tv-settings.sh restore, and adb uninstall.
- Never push, publish or share my local.properties, my keys or my signing
  folder.
```

## Install on your own Android TV

### What you need

| Need | Notes |
| --- | --- |
| An Android TV or Google TV with Android 11 or newer | It was built and tested on a Chromecast with Google TV (Google TV 14) and an Android TV 11 television. |
| A computer with adb, on the same network as the TV | adb is in the Android SDK's platform-tools. |
| A JDK 17 or newer and the Android SDK | To build. You need SDK platform 37 and build tools 36. |
| Stremio on the TV | Required. Titles open in Stremio. |
| A Trakt account | Required and free. Set your country in your Trakt profile: the Netflix button uses it. |
| A Trakt API application | Required and free. You make your own (step 2). |
| YouTube, Netflix, BBC iPlayer | Each one is optional. The launcher hides what the TV does not have. |

### 1. Connect adb to the TV

1. On the TV, turn on the developer options. On most TVs: Settings, About, then select the build number seven times. The menu names differ between TVs.
2. In the developer options, turn on network debugging or Wireless debugging.
3. Connect from the computer. Google TV's Wireless debugging needs a pairing code first:

```sh
adb pair <tv-address>:<pairing-port>     # Google TV, Wireless debugging only
adb connect <tv-address>:<port>          # many Android TVs use port 5555
adb devices                              # the first column is the serial for the steps below
```

### 2. Make a Trakt API application

1. Sign in at trakt.tv, and open your settings, Your API Apps, New Application.
2. Give it a name. Set the redirect URI to `urn:ietf:wg:oauth:2.0:oob`. The launcher uses Trakt's device sign-in, which needs no web page of its own.
3. Save it. Trakt shows the application's Client ID and Client Secret.

Keep the secret private. Each person who builds the launcher uses their own application.

### 3. Set up local.properties

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

### 4. Build and install

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

### 5. Back up the TV's settings

Before you change anything, save the settings that the next steps change: the screensaver, the accessibility services and the Home app.

```sh
scripts/tv-settings.sh backup <adb-serial> tv-settings-before.txt
```

Keep the file. It holds no address or serial. The script never writes over an earlier backup.

### 6. Sign in to Trakt

Open the launcher. It shows a Trakt device code. On a phone, open trakt.tv/activate and enter the code. "Skip for Now" opens Home without the Trakt rows.

### 7. Let the launcher read TV channels

```sh
adb -s <adb-serial> shell pm grant "$(scripts/app-id.sh)" android.permission.READ_TV_LISTINGS
```

Without this, the YouTube row and the Stremio fallback row are hidden.

### 8. The screensaver (optional)

The launcher's Settings, Screensaver, opens the TV's screensaver settings, where you can choose it if the TV lists it. You can also set it with adb:

```sh
adb -s <adb-serial> shell settings put secure screensaver_components "$(scripts/app-id.sh)/app.opentvlauncher.screensaver.ScreensaverService"
```

The class name stays `app.opentvlauncher...` whatever your app id is.

### 9. The Home override (optional)

The Home override brings the launcher back when Google TV Home comes to the front, or after a short press of the Home key. It is off after installation.

```sh
scripts/home-override.sh on <adb-serial>
```

Or turn it on in the TV's Settings, Accessibility, Personal TV Launcher Home. The script adds only this service and keeps every other enabled service.

### Undo

| Part | Undo |
| --- | --- |
| Home override | `scripts/home-override.sh off <adb-serial>`, or Settings, Accessibility, Personal TV Launcher Home, Off. A force-stop of the launcher also turns it off. |
| Every setting from step 5 | `scripts/tv-settings.sh restore <adb-serial> tv-settings-before.txt` |
| The app | `adb -s <adb-serial> uninstall "$(scripts/app-id.sh)"`, or the TV's Settings, Apps |
| Trakt | Remove the application from your Trakt account's connected apps on trakt.tv |

### Limits

- **Never disable Google TV Home** (`com.google.android.apps.tv.launcherx`) on a Chromecast with Google TV. On 24 Oct 2026 this made the Chromecast start its setup recovery screen hundreds of times at boot, and the screen stayed black. The launcher never needs it disabled.
- The launcher is not a Home app. It has no Home intent filter, so the Home key always goes to the TV's own home first. With the override on, the launcher comes back 120 ms later.
- The Home override was tested only with Google TV Home. On a TV with Android TV Home, such as a Philips Android TV, it is untested, and by its code it does nothing there: it watches only for Google TV Home's screen.
- The Netflix button needs Trakt's `watchnow=netflix` filter. Trakt answers for the country in your Trakt profile. It was checked only with a Trakt VIP account. A title also needs its Netflix id on Wikidata, Netflix on the TV, and a place on your watchlist, in your recommendations, or in Continue Watching with at least one play.
- The YouTube row needs the grant in step 7 and YouTube's "Recommended" channel, which the launcher finds by its English name.
- The text is English only, and dates use UK formats.

## Test on the computer

```sh
./gradlew testDebugUnitTest lintRelease
```

The tests check the sample data, the layout numbers, the colour contrast, the D-pad focus map, the Trakt sign-in, the rules for the Trakt rows and the scripts. The Trakt tests use a local mock server, never Trakt. The sample titles are made up, and release builds have no sample data. The focus tests run the real home screen with Robolectric on a 960 × 540 dp screen. They do not replace a check on the TV.

`HomeClearanceTest` walks the focus through each row at its first, middle and last item, at 960 × 540 dp and 1280 × 720 dp (a 1.5× TV). It measures the drawn bounds of the focused item with its 1.1 scale and its shadow. It fails when a focused item comes nearer than 12 dp to any text or to another row, when two texts overlap, or when a text is cut short with "…". `RowPivotTest` checks the fixed focus position.

To check the same things on a TV, run the screenshot pass. It walks the focus through Home and saves a capture at each stop. It starts only when the TV shows a home screen or the screensaver and nothing plays. The captures go to a new temporary folder, about 2 MB each. Delete the folder after you look at them.

```sh
scripts/screenshot-pass.sh <adb-serial> [output-folder]
```

The debug build installs as the app id with `.debug` at the end, next to the release build.

## Trakt Sign-In

The first start on each TV shows Trakt's device code. Open trakt.tv/activate on a phone and enter the code. "Skip for Now" opens Home without Trakt rows.

- A code lasts 10 minutes. When it runs out, the screen shows a new code by itself, up to 5 times (about an hour). After that, "Get a New Code" starts again.
- The screen asks Trakt only while it is in front. When the TV sleeps or another app opens, it stops, and it shows a new code on return.
- Tokens are stored in DataStore on the TV. They are not in cloud backups or device transfers. The launcher refreshes the access token and refresh token when a quarter of the access token lifetime remains. After HTTP 401, it refreshes once and retries the request.
- If Trakt refuses the refresh token, the launcher signs out and shows the sign-in screen at the next start.

## Home Rows

Home draws from a saved copy, `files/home-state.json`, so the first frame never waits for the network. A refresh reads Trakt and the TV provider, then saves a new copy.

- Refresh runs after sign-in and when the launcher returns to the foreground, subject to a one-minute interval. WorkManager schedules an hourly refresh when a network is available.
- If a Trakt request fails, Home keeps the saved rows. The status shows Trakt Unavailable and the saved time, or No Saved Data.
- Rows, from the top: apps, Continue Watching, Recommended on YouTube, Top Picks for You, From Your Watchlist. An empty row is hidden.
- The apps row has YouTube, Stremio, Netflix and BBC iPlayer, each only when the TV has it, then a narrow All tile for All Apps.
- With 1 to 3 Continue Watching titles (paused items and up next), the row adds the newest watchlist titles, up to 8 cards, after a 40 dp gap. From Your Watchlist starts after those titles. With no Continue Watching titles, the row is hidden.
- Banner: up to 3 Continue Watching slides, then one Top Pick and one watchlist title in turn, up to 3 of each.
- Banner focus: Right moves through the available app buttons, Details and the current slide's dot. On the dot, Right shows the next slide and wraps to the first. Left returns to the first button. Down then Up returns to the first button.
- Continue Watching: Trakt's paused items, and the next aired episode of each started show ("Up next"), newest activity first, one card for each show. An item paused, or a show last watched, more than 180 days ago is hidden. Shows hidden from progress or dropped on Trakt have no up-next card.
- Top Picks for You requests up to 30 recommended films and 30 recommended shows. It removes watched, rated and Continue Watching titles. It displays up to 30 remaining titles, with films and shows in turn.
- From Your Watchlist shows up to 40 Trakt titles, with the most recent additions first. After Trakt confirms an addition, the launcher moves that title to the front and removes it from Top Picks. Watched, rated and Continue Watching titles remain hidden.
- Both rows hide titles that are watched or rated, and titles in Continue Watching. Both use poster cards with the year and genre.
- Trakt splits the watched, watchlist and ratings lists into pages of at most 250. The launcher reads every page: for a large account, about 10 requests at each refresh.
- Recommended on YouTube: the YouTube app's "Recommended" channel in the TV provider, and no other YouTube channel. Without that channel, or without `READ_TV_LISTINGS`, the row is hidden.
- Without Trakt, Stremio's own "Continue Watching" channel fills the Continue Watching row, if Stremio publishes one.
- A YouTube card opens the video's own intent.

## Continue Watching Menu

Press the remote's menu key, or hold OK, on a Continue Watching card. The menu offers the changes that fit the title:

- Mark as Watched: clears the paused position (`DELETE /sync/playback/{id}`), then adds a play (`POST /sync/history`). The card leaves the row.
- Mark as Unwatched: removes every play of the film or episode (`POST /sync/history/remove`). The card stays.
- Reset Progress: clears the paused position. A show's card clears all of its paused episodes. The card leaves the row.
- Remove from Watchlist: only for a title on the watchlist (`POST /sync/watchlist/remove`).

Every change except Mark as Watched needs a second OK within 5 seconds. Each change belongs to the Trakt session the menu opened in, so a sign-out or a new sign-in stops it. If Trakt does not confirm, the row offers a retry. Back closes the menu.

## Top Bar

Home and All Apps share a top bar: the Home, Movies, Shows and Apps tabs, then the Screensaver and Settings buttons. An Assistant button shows first when the TV answers the assistant request. The tabs and the buttons each sit on a pill that blurs the art behind it, with a faint light tint, a little stronger while focus is in the bar. The clock, in white, sits at the right end of the bar on every tab. Tab labels are light grey, the current page's too. The focused item takes a light fill with dark text. While focus is in the bar, the banner shows closed on every tab, so the same title stays still when the tab changes. The bar scrolls away with the banner when focus goes down into the rows.

- Up from the banner, or from the apps row when there is no banner, goes to the bar on the current tab. Up from the first row of All Apps goes to the Apps tab.
- Left and Right move along the tabs and buttons and stop at the ends. When focus rests on a tab for 250 ms, its page shows below, and focus stays in the bar. A tab that focus only passes is not built, so the bar moves quickly.
- Down, from a tab or a button, goes back to the item that last had focus on the current tab's page, and a banner button opens the banner again. OK on a tab does the same.
- Back on Movies, Shows or All Apps goes to the bar on that tab. Back again goes to the Home tab. Back on the Home tab goes to the first app tile.
- Start Screensaver opens the launcher's screensaver. Any key closes it, and focus goes back to the button.
- Settings opens the launcher's Settings. Back goes back to the button.
- Assistant opens Google's assistant over the launcher.
- The Home button always goes to the Home tab, with focus on the first app tile.

Movies and Shows filter the Trakt lists that Home already has, so they need no new Trakt reads. Each has a banner and the rows Continue Watching, Top Picks for You and From Your Watchlist, for films or for shows, with the same fill. They have no apps row and no YouTube row. Their first row takes the apps row's place: Down from the tab goes to it, it does not scroll, and Up from it opens the banner. A tab with no rows, such as Movies and Shows without Trakt, is not in the bar. Each tab keeps its own row, cards and banner slide while another tab shows.

## Focus and Spacing

- Up and Down move one row. Focus returns to its remembered card, or its first card. In Continue Watching, new titles before the remembered card make a D-pad entry start at the first card. No row is skipped, even a row with one card on the left.
- Rows keep their card and scroll position, and the banner keeps its slide. Returning from Details restores the opened card. It is saved state, so it also stays when Android recreates the launcher with its saved state. The launcher is marked stateNotNeeded, as home apps are, so after Android ends its process the rows may start again at their first card.
- Rows keep the focused item in one place, like Google TV. Right on the first item brings the second item to where the first was, 58 dp from the screen edge, and the row slides under it. When the row cannot slide further, the focus moves along the last items.
- The apps row's large tiles keep their size. With all four apps, they and the narrow All tile fill the space between the margins exactly at 960 dp, so the row never slides. With fewer apps the row is shorter and starts at the left margin.
- The row slides with the design's scroll spring. Compose 1.12 marks that setting as deprecated but still uses it. If a later Compose ignores it, the row slides with Compose's own spring.
- A focused item keeps at least 12 dp from all text. The space above the cards is 51 dp for wide rows and 55 dp for poster rows. The space between the cards and their titles is 18 dp and 22 dp. The cards sit at 421 dp, not the design's 410, to make this room.
- Compose draws the banner's 40 sp title 52 to 54 dp tall, not 44. The meta line sits at 179 dp, 4 dp below the design, so the two never touch.
- Focus glow: the focused card, poster or app tile has a soft light round it in its art's colour, lightened to at least 55 % lightness and 40 % saturation. Grey art glows warm white at half strength. It is light, not an outline: 42 % at the source, fading out over 48 dp, 4 dp low. `FocusGlow.ENABLED` turns it off.

## Selecting a Title

Settings has one choice for OK on a film or show, "Selecting a Title":

- Show Details (the first setting): the launcher's details page with the backdrop, title logo, year, runtime, genres, Trakt rating and summary. Back returns to the same card.
- Open in Stremio: Stremio's title page, `stremio:///detail/{movie|series}/{imdb}`.

Without Stremio on the TV, OK always opens Details, and Settings offers only Show Details. The saved choice stays, so it works again once Stremio is installed.

Stremio and Resume open the title in Stremio. Netflix opens the title's page in Netflix, for a title that is on Netflix. While the app opens, the hand-off screen shows "Opening in Stremio" or "Opening in Netflix", until the app is in front or for 15 s. A button for an app that the TV does not have is hidden.

On Details, Remove from Watchlist changes to Add Back to Watchlist for 10 seconds. After that, the request is sent and the button shows Removing from Watchlist. The request cannot be undone once sent. After confirmation, the title leaves the row and the button shows Add to Watchlist. If removal is not confirmed, the button shows Retry Removal.

## Settings

The bar's Settings button, or All Apps, then Personal TV Launcher:

- Trakt Account: sign in, or sign out with two presses of OK. Signing out removes the Trakt rows from Home.
- Selecting a Title: see above.
- Screensaver: opens the TV's screensaver settings.
- About: the version.

## All Apps

All Apps is the bar's Apps tab. The narrow All tile on Home opens it with focus on its first tile.

- On Home: the apps of the apps row that the TV has. With none of them, this group is left out.
- Other Apps: every other TV app, in label order, with its own icon. The list is read again each time the launcher comes to the front.
- System: Settings, the Play Store, the TV's own home app with its own name (such as Google TV Home), and Personal TV Launcher (the launcher's own Settings).

## Screensaver

The screensaver uses saved Top Picks that have artwork links. If none have links, it uses the watchlist, then Continue Watching. Offline images must already be cached. It fades in once the first artwork and logo have loaded, or after 3 seconds. It changes title every 40 seconds and changes the order each day. Artwork brightness is 45%. Logos are white at 70% opacity, and a small logo is never enlarged. When no logo is available, it shows the title. Under the logo is a line such as "Drama series from 2012", followed by the first one or two sentences of the summary. With no eligible titles, the screen stays dark. Step 8 of the install sets it up.

## Artwork

- Films and shows use Metahub, Stremio's own image service, by IMDb id. If Metahub has no image, Trakt's image is the fallback.
- Each use takes the smallest Metahub size that is still sharp: `small` for cards and posters, `medium` (1920 × 1080) for the banner.
- Title logos on the details page and the screensaver come from Trakt only. Metahub's logos can be in another language, and Metahub does not say which. A title with no Trakt logo shows its name as text.
- Coil downloads and decodes images. Its disk cache is limited to 150 MB in `cache/art`. The launcher keeps up to 48 MB of processed bitmaps in memory. Bitmaps use the display size and include the banner fade.
- The page tint comes from the hue of the focused art. Grey art gets a neutral tint.
- A missing image shows the placeholder colour and is not fetched again for 10 minutes.

## Home Override

Google TV Home stays enabled and stays the TV's Home app. An accessibility service in the launcher, Personal TV Launcher Home, brings the launcher back over it: when Google's home screen comes to the front, or after a short press of the Home key, it starts the launcher 120 ms later. The Home key still goes on to Android. Google's home reports its window late, so a window that comes just after a start only makes the service check again 1.5 s later. Google TV Home's other screens, such as voice search results, stay. The home app's tile in All Apps opens it on purpose, and it stays until another app shows or Home is pressed.

The service starts the launcher with an explicit intent, never a Home intent, so the remote's YouTube and Netflix buttons keep working. Each start waits 120 ms, and any other screen that comes to the front in that time, such as Netflix, a cast or Settings, cancels it. It runs in its own process. It counts automatic starts until another app comes to the front, or the launcher stays in front for 2 s without a break. A fifth in 10 s with neither is a loop between the launcher and Google's home, and it turns itself off. Home key presses never count and never clear the count. When the service connects, it starts the launcher only if the active window is known to be Google's home screen.

Step 9 of the install turns it on, and the Undo table turns it off.

## Versions

Checked on 23 and 24 October 2026. The versions are in `gradle/libs.versions.toml`.

| Part | Version | Source |
| --- | --- | --- |
| Gradle | 9.8.2 | services.gradle.org/versions/current |
| Android Gradle Plugin | 9.4.1 | Google Maven. AGP 9.4 release notes: Gradle 9.6 or newer, JDK 17, API 37 or lower |
| Kotlin | 2.4.21 | Maven Central. AGP 9 compiles Kotlin itself, and the root build file raises its Kotlin version |
| Compose BOM | 2026.09.00 (Compose 1.12.1) | Google Maven |
| Compose for TV (`androidx.tv:tv-material`) | 1.1.0 | Google Maven |
| compileSdk and targetSdk | 37 (Android 17) | SDK repository: `platforms;android-37.0` is on the stable channel |
| minSdk | 30 (Android 11) | The oldest TV tested |
| OkHttp | 5.5.1 | Maven Central |
| kotlinx-serialization-json | 1.12.0 | Maven Central |
| DataStore Preferences | 1.3.0 | Maven Central |
| WorkManager | 2.12.0 | Google Maven |
| Coil (`coil-core`, `coil-network-okhttp`) | 3.6.3 | Maven Central |

## Licence

MIT. See [LICENSE](LICENSE). The Autospawn Sans fonts are under the SIL Open Font License, in `app/src/main/assets/fonts/OFL.txt`. App names and marks belong to their owners.
