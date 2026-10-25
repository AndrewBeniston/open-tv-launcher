# Features

What the launcher shows and does, screen by screen. [The Home override](home-override.md) has its own page.

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

The screensaver uses saved Top Picks that have artwork links. If none have links, it uses the watchlist, then Continue Watching. Offline images must already be cached. It fades in once the first artwork and logo have loaded, or after 3 seconds. It changes title every 40 seconds and changes the order each day. Artwork brightness is 45%. Logos are white at 70% opacity, and a small logo is never enlarged. When no logo is available, it shows the title. Under the logo is a line such as "Drama series from 2012", followed by the first one or two sentences of the summary. With no eligible titles, the screen stays dark. [Install by hand](install.md), step 8, sets it up.

## Artwork

- Films and shows use Metahub, Stremio's own image service, by IMDb id. If Metahub has no image, Trakt's image is the fallback.
- Each use takes the smallest Metahub size that is still sharp: `small` for cards and posters, `medium` (1920 × 1080) for the banner.
- Title logos on the details page and the screensaver come from Trakt only. Metahub's logos can be in another language, and Metahub does not say which. A title with no Trakt logo shows its name as text.
- Coil downloads and decodes images. Its disk cache is limited to 150 MB in `cache/art`. The launcher keeps up to 48 MB of processed bitmaps in memory. Bitmaps use the display size and include the banner fade.
- The page tint comes from the hue of the focused art. Grey art gets a neutral tint.
- A missing image shows the placeholder colour and is not fetched again for 10 minutes.
