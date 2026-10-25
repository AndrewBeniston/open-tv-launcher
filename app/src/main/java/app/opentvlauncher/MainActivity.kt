package app.opentvlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.lifecycleScope
import app.opentvlauncher.data.AppKind
import app.opentvlauncher.data.AppTile
import app.opentvlauncher.data.CardAction
import app.opentvlauncher.data.HomeContent
import app.opentvlauncher.home.ChangeResult
import app.opentvlauncher.home.HomeMapper
import app.opentvlauncher.home.HomeState
import app.opentvlauncher.home.RefreshWorker
import app.opentvlauncher.home.Title
import app.opentvlauncher.home.TitleChange
import app.opentvlauncher.home.TitleChanges
import app.opentvlauncher.netflix.NetflixRules
import app.opentvlauncher.screensaver.ScreensaverScreen
import app.opentvlauncher.screensaver.screensaverTitles
import app.opentvlauncher.settings.TitleAction
import app.opentvlauncher.system.GoogleHomePass
import app.opentvlauncher.system.InstalledApps
import app.opentvlauncher.system.SystemRoutes
import app.opentvlauncher.system.TitleApp
import app.opentvlauncher.system.TitleAppIcons
import app.opentvlauncher.trakt.SignIn
import app.opentvlauncher.ui.allapps.AllAppsScreen
import app.opentvlauncher.ui.details.DetailsScreen
import app.opentvlauncher.ui.details.HandOffScreen
import app.opentvlauncher.ui.details.WatchlistButton
import app.opentvlauncher.ui.details.watchlistButton
import app.opentvlauncher.ui.focus.activePage
import app.opentvlauncher.ui.home.BarButton
import app.opentvlauncher.ui.home.BarItem
import app.opentvlauncher.ui.home.BarRequest
import app.opentvlauncher.ui.home.BarShapes
import app.opentvlauncher.ui.home.HomeScreen
import app.opentvlauncher.ui.home.LauncherTabs
import app.opentvlauncher.ui.home.PageScroll
import app.opentvlauncher.ui.home.Tab
import app.opentvlauncher.ui.home.pageFade
import app.opentvlauncher.ui.home.rememberTabFade
import app.opentvlauncher.ui.menu.TitleMenu
import app.opentvlauncher.ui.menu.TitleMenuStatus
import app.opentvlauncher.ui.menu.next
import app.opentvlauncher.ui.menu.titleMenuChanges
import app.opentvlauncher.ui.settings.SettingsScreen
import app.opentvlauncher.ui.signin.SignInFlow
import app.opentvlauncher.ui.signin.SignInScreen
import app.opentvlauncher.ui.signin.SignInState
import app.opentvlauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The full-screen places. Home holds the top bar's pages: Home itself and All Apps (the Apps
 * tab). The details page and the hand-off sit over Home, so Home keeps its focus.
 */
private sealed interface Screen {
    data object Home : Screen

    /** The launcher's Settings. [fromBar]: opened from the bar's Settings button, not from All Apps. */
    data class Settings(val fromBar: Boolean) : Screen
}

class MainActivity : ComponentActivity() {
    private var homeRequests by mutableIntStateOf(0)
    private var screen by mutableStateOf<Screen>(Screen.Home)
    private var homeStartApp by mutableIntStateOf(0)

    /** The top bar's page; whether the bar has focus; and the bar's focus requests. */
    private var tab by mutableStateOf(Tab.Home)
    private var barFocused by mutableStateOf(false)
    private var barRequest by mutableStateOf<BarRequest?>(null)
    private val pageScroll = PageScroll()

    /** All Apps under the bar: whether it takes focus when it opens, on which tile, and requests to enter it from the bar. */
    private var appsTakesFocus = false
    private var appsStartOnLauncher = false
    private var appsEntries by mutableIntStateOf(0)

    /** Whether the TV answers ACTION_ASSIST, so the bar shows its Assistant button. Read at each start. */
    private var assistant by mutableStateOf(false)

    /** The launcher's own screensaver as a full-screen page, from the bar's button. */
    private var screensaver by mutableStateOf(false)

    /** The details page over Home, or null. */
    private var details by mutableStateOf<Title?>(null)

    /** The hand-off over Home and the details page, from the key press until its app is in front. */
    private var handOff by mutableStateOf<HandOff?>(null)

    /** Goes up each time the details page closes, so Home gives focus back to the same card. */
    private var homeFocusReturns by mutableIntStateOf(0)

    /**
     * The Continue Watching menu over Home, or null; where its last choice is; and the session
     * generation it opened in, which every change it sends belongs to.
     */
    private var menu by mutableStateOf<Title?>(null)
    private var menuStatus by mutableStateOf<TitleMenuStatus>(TitleMenuStatus.Ready)
    private var menuGeneration = 0L

    /** The TV's apps for All Apps, read again each time the launcher comes to the front. */
    private var installed by mutableStateOf<InstalledApps.Groups?>(null)

    /** Stremio and Netflix, with their icons, for the title buttons; read with [installed]. */
    private var titleApps by mutableStateOf(TitleAppIcons.Unknown)

    /** Whether Stremio is installed. Before the first read it counts as installed, as the buttons do. */
    private val stremioInstalled: Boolean get() = titleApps.installed?.contains(TitleApp.Stremio.packageName) ?: true

    /** The current sign-in's id: only its own Netflix answer gives a title a Netflix button. */
    private var signInId by mutableStateOf<String?>(null)

    /**
     * One more at each start and resume, and when the Netflix answer stops counting after 7 days:
     * the Netflix buttons are drawn again for the time now, with or without a new HomeState.
     */
    private var netflixChecks by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { app.titleChanges.results.collect(::showChangeResult) }
        lifecycleScope.launch { app.session.signInId.collect { signInId = it } }
        try {
            RefreshWorker.schedule(this)
        } catch (_: IllegalStateException) {
            // WorkManager is not set up (some tests). The refresh on start still runs.
        }
        setContent {
            LauncherTheme(art = app.art, titleApps = titleApps) {
                // Nothing draws until DataStore says whether the TV is signed in: the window
                // shows the surface colour for that moment.
                val signIn by app.session.state.collectAsState(initial = null)
                var signInAttempt by remember { mutableIntStateOf(0) }
                val current = signIn ?: return@LauncherTheme
                // A new sign-in fills the Trakt rows at once. The start itself refreshes in onStart.
                var shownSignIn by remember { mutableStateOf(signIn) }
                LaunchedEffect(signIn) {
                    if (signIn == SignIn.SignedIn && shownSignIn != SignIn.SignedIn) app.home.refresh()
                    shownSignIn = signIn
                }
                val homeState by app.home.state.collectAsState()
                // While the launcher is in front, the Netflix buttons are drawn again when the answer stops counting.
                val inFront = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
                val netflixExpiry = NetflixRules.expiresAt(homeState.netflix)
                LaunchedEffect(netflixExpiry, inFront) {
                    val expiry = netflixExpiry?.takeIf { inFront } ?: return@LaunchedEffect
                    var waited = false
                    while (System.currentTimeMillis() <= expiry) {
                        waited = true
                        delay(expiry - System.currentTimeMillis() + 1)
                    }
                    if (waited) netflixChecks++
                }
                // The apps row leaves out the apps that the TV does not have, once they are read.
                val onHomeApps = installed?.onHome
                val content = remember(homeState, signInId, netflixChecks, onHomeApps) {
                    HomeMapper.content(homeState, signIn = signInId, installed = onHomeApps)
                }
                val tabContent = remember(homeState, signInId, netflixChecks) {
                    Tab.entries.mapNotNull { t -> t.kind?.let { t to HomeMapper.content(homeState, kind = it, signIn = signInId) } }.toMap()
                }
                val titleAction by app.settings.titleAction.collectAsState(initial = TitleAction.ShowDetails)
                // Home's saved state (remembered cards, row scroll, banner slide) while All Apps or Settings shows.
                val savedScreens = rememberSaveableStateHolder()

                if (current == SignIn.SignedOut) {
                    val flow = remember(signInAttempt) { SignInFlow(app.trakt, app.session).states() }
                    // Polls only while the screen is in front. A return to the front starts with a new code.
                    val state by flow.collectAsStateWithLifecycle(initialValue = SignInState.Loading)
                    SignInScreen(
                        state = state,
                        onNewCode = { signInAttempt++ },
                        onSkip = { lifecycleScope.launch { app.session.skip() } },
                    )
                    return@LauncherTheme
                }

                when (val place = screen) {
                    is Screen.Settings -> SettingsScreen(
                        signIn = current,
                        titleAction = titleAction,
                        stremioInstalled = stremioInstalled,
                        version = BuildConfig.VERSION_NAME,
                        onSignIn = {
                            screen = Screen.Home
                            lifecycleScope.launch { app.session.signOut() }
                        },
                        onSignOut = {
                            screen = Screen.Home
                            lifecycleScope.launch {
                                app.session.signOut()
                                app.home.refresh()
                            }
                        },
                        onTitleAction = { lifecycleScope.launch { app.settings.setTitleAction(it) } },
                        onScreensaver = ::openScreensaverSettings,
                        onBack = {
                            screen = Screen.Home
                            if (place.fromBar) {
                                askBar(BarItem.OfButton(BarButton.Settings))
                            } else {
                                // Back to All Apps, on the Personal TV Launcher tile.
                                openApps(startOnLauncher = true)
                            }
                        },
                    )
                    Screen.Home -> savedScreens.SaveableStateProvider("home") { HomeWithOverlays(content, tabContent, titleAction) }
                }
            }
        }
    }

    @Composable
    private fun HomeWithOverlays(content: HomeContent, tabContent: Map<Tab, HomeContent>, titleAction: TitleAction) {
        val covered = details != null || handOff != null || screensaver
        val menuTitle = menu
        // Movies and Shows show only when they have a row, so never as an empty page.
        val tabs = Tab.entries.filter { it.kind == null || tabContent[it]?.rows.orEmpty().isNotEmpty() }
        LaunchedEffect(tabs) { if (tab !in tabs) showHomeTabInBar() }
        // Each of Movies and Shows keeps its own rows, cards and slide while another tab shows.
        val savedTabs = rememberSaveableStateHolder()
        val fade = rememberTabFade(tab)
        val barShapes = remember { BarShapes() }
        Box(Modifier.fillMaxSize()) {
            LauncherTabs(
                tabs = tabs,
                tab = tab,
                buttons = listOfNotNull(BarButton.Assistant.takeIf { assistant }, BarButton.Screensaver, BarButton.Settings),
                barRequest = barRequest,
                scroll = pageScroll,
                active = !covered && menuTitle == null,
                onTab = ::showTab,
                onButton = ::pressBarButton,
                onDown = ::enterPage,
                onBarFocus = { barFocused = it },
                shapes = barShapes,
            ) {
                val onHomeTab = tab == Tab.Home
                // Home stays composed under the other tabs, as under the details page, so it keeps its place.
                HomeScreen(
                    content = content,
                    homeRequests = homeRequests,
                    startApp = homeStartApp,
                    onOpenApp = { app -> if (app.kind == AppKind.AllApps) openApps() else openApp(app) },
                    onOpenCard = { openCard(it, titleAction.withStremio(stremioInstalled)) },
                    onPlay = ::play,
                    focusReturns = homeFocusReturns,
                    // Under a full-screen page, Home draws nothing and takes no focus or keys. Under
                    // the menu it still shows, dimmed.
                    active = onHomeTab && !covered && menuTitle == null,
                    onCardMenu = { action -> if (action is CardAction.OpenTitle) openMenu(action.title) },
                    onUpFromTop = {
                        askBar(BarItem.OfTab(Tab.Home))
                        true
                    },
                    scroll = pageScroll.takeIf { onHomeTab },
                    barFocused = barFocused,
                    barShapes = barShapes,
                    modifier = Modifier.pageFade { if (covered) 0f else fade.alpha(Tab.Home) },
                )
                if (tab == Tab.Apps) SideEffect { pageScroll.offset = { 0f } }
                if (tab == Tab.Apps || Tab.Apps in fade.outgoing) {
                    savedTabs.SaveableStateProvider(Tab.Apps.name) {
                        Box(Modifier.pageFade { fade.alpha(Tab.Apps) }.activePage(tab == Tab.Apps)) {
                            AllAppsScreen(
                                apps = content.apps.filter { it.kind != AppKind.AllApps },
                                installed = installed,
                                onOpenApp = ::openApp,
                                onOpenInstalled = { openPackage(it.packageName) },
                                onOpenStockHome = {
                                    SystemRoutes.stockHome(this@MainActivity)?.let {
                                        // On purpose, so the Home override lets Google's home stay.
                                        GoogleHomePass.give(this@MainActivity)
                                        launch(it)
                                    }
                                },
                                onOpenSettings = { launch(SystemRoutes.settings()) },
                                onOpenLauncherSettings = { screen = Screen.Settings(fromBar = false) },
                                onBack = {
                                    // Back from the page goes to the bar, on Apps. Back again goes to Home.
                                    if (barFocused) showHomeTabInBar() else askBar(BarItem.OfTab(Tab.Apps))
                                },
                                startOnLauncher = appsStartOnLauncher,
                                underBar = true,
                                takeFocus = appsTakesFocus,
                                entryRequests = appsEntries,
                                onUpFromTop = { askBar(BarItem.OfTab(Tab.Apps)) },
                                active = tab == Tab.Apps,
                            )
                        }
                    }
                }
                // Movies and Shows: the shown page, and a page fading out after a change between them.
                (fade.outgoing + tab).filter { it.kind != null && it in tabs }.forEach { page ->
                    val shown = tabContent[page] ?: return@forEach
                    key(page) {
                        savedTabs.SaveableStateProvider(page.name) {
                            HomeScreen(
                                content = shown,
                                homeRequests = 0,
                                takeFocus = false,
                                onOpenApp = {},
                                onOpenCard = { openCard(it, titleAction.withStremio(stremioInstalled)) },
                                onPlay = ::play,
                                focusReturns = homeFocusReturns,
                                active = page == tab && !covered && menuTitle == null,
                                onCardMenu = { action -> if (action is CardAction.OpenTitle) openMenu(action.title) },
                                onUpFromTop = {
                                    askBar(BarItem.OfTab(page))
                                    true
                                },
                                scroll = pageScroll.takeIf { page == tab },
                                barFocused = barFocused,
                                barShapes = barShapes,
                                modifier = Modifier.pageFade { if (covered) 0f else fade.alpha(page) },
                            )
                        }
                    }
                }
            }
            // Back on the bar's Home tab goes to YouTube, as Back from anywhere on Home does.
            BackHandler(enabled = barFocused && tab == Tab.Home && !covered && menuTitle == null) {
                homeStartApp = 0
                homeRequests++
            }
            // Back on Movies or Shows goes to the bar, on that tab. Back again goes to the Home tab.
            BackHandler(enabled = tab.kind != null && !covered && menuTitle == null) {
                if (barFocused) showHomeTabInBar() else askBar(BarItem.OfTab(tab))
            }
            menuTitle?.let { title ->
                val homeState by app.home.state.collectAsState()
                val listed = title.key?.let { it in homeState.trakt?.watchlistKeys.orEmpty() } == true
                TitleMenu(
                    title = withNetflixNow(title, homeState),
                    changes = titleMenuChanges(title, listed),
                    status = menuStatus,
                    onPlay = {
                        closeMenu()
                        handOff = HandOff(title, TitleApp.Stremio)
                    },
                    onNetflix = {
                        closeMenu()
                        openNetflix(title)
                    },
                    onChoose = { chooseInMenu(title, it) },
                    onConfirmEnded = { if (menuStatus is TitleMenuStatus.Confirming) menuStatus = TitleMenuStatus.Ready },
                    onBack = ::closeMenu,
                )
            }
            details?.let { title ->
                val homeState by app.home.state.collectAsState()
                val phases by app.watchlistUndo.phases.collectAsState()
                val key = title.key
                val button = watchlistButton(key?.let(phases::get), key != null && key in homeState.trakt?.watchlistKeys.orEmpty())
                DetailsScreen(
                    title = withNetflixNow(title, homeState),
                    watchlist = button,
                    onPlay = { handOff = HandOff(title, TitleApp.Stremio) },
                    onNetflix = { openNetflix(title) },
                    onWatchlist = {
                        when (button) {
                            WatchlistButton.Undo -> app.watchlistUndo.undo(title)
                            WatchlistButton.Remove, WatchlistButton.RetryRemove -> app.watchlistUndo.remove(title)
                            WatchlistButton.Removing -> Unit
                            WatchlistButton.Add -> app.scope.launch { app.home.setWatchlisted(title, on = true) }
                        }
                    },
                    onBack = ::closeDetails,
                    active = handOff == null,
                )
            }
            if (screensaver) {
                val homeState by app.home.state.collectAsState()
                val titles = remember(homeState) { screensaverTitles(homeState, System.currentTimeMillis() / DAY_MILLIS) }
                val page = remember { FocusRequester() }
                // Any key closes it, Back too, and focus goes back to the bar's button.
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(page)
                        .focusable()
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown) closeScreensaver()
                            true
                        },
                ) { ScreensaverScreen(titles) }
                LaunchedEffect(Unit) { page.requestFocus() }
                BackHandler(onBack = ::closeScreensaver)
            }
            handOff?.let { pending ->
                val title = pending.title
                HandOffScreen(title, onBack = ::endHandOff, app = pending.app)
                LaunchedEffect(pending) {
                    // Draw the hand-off first, then ask the app. It covers the wait until the app
                    // is in front (onStop), or 15 s if it never comes.
                    withFrameNanos { }
                    withFrameNanos { }
                    val intent = if (pending.app == TitleApp.Netflix) title.netflixId?.let(SystemRoutes::netflixTitle) else SystemRoutes.stremioTitle(title)
                    if (intent == null || !launch(intent)) {
                        endHandOff()
                        return@LaunchedEffect
                    }
                    delay(15_000)
                    endHandOff()
                }
            }
        }
    }

    /** App start and each return to the front: refresh, unless a refresh ran in the last minute. */
    override fun onStart() {
        super.onStart()
        netflixChecks++
        lifecycleScope.launch { app.home.refreshIfOlderThan(60_000) }
        assistant = packageManager.resolveActivity(Intent(Intent.ACTION_ASSIST), 0) != null
        lifecycleScope.launch {
            val onHome = HomeMapper.apps.mapNotNull { it.packageName }.toSet()
            val iconPx = (22 * resources.displayMetrics.density).roundToInt()
            installed = withContext(Dispatchers.IO) { InstalledApps.read(this@MainActivity, onHome, iconPx) }
            titleApps = withContext(Dispatchers.IO) { TitleAppIcons.read(this@MainActivity, iconPx) }
        }
    }

    override fun onResume() {
        super.onResume()
        netflixChecks++
    }

    /** Stremio, or another app, is in front: the hand-off has done its job. */
    override fun onStop() {
        super.onStop()
        if (handOff != null) endHandOff()
    }

    /** The Home button, or the launcher's own icon, while the launcher is already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screen = Screen.Home
        tab = Tab.Home
        details = null
        handOff = null
        menu = null
        screensaver = false
        homeStartApp = 0
        homeRequests++
    }

    /** OK on a card, or "Details" in the banner: follows the Selecting a Title setting. */
    private fun openCard(action: CardAction, titleAction: TitleAction) {
        when (action) {
            is CardAction.OpenTitle -> if (titleAction == TitleAction.ShowDetails) details = action.title else handOff = HandOff(action.title, TitleApp.Stremio)
            is CardAction.OpenVideo -> openVideo(action)
            is CardAction.OpenNetflix -> openNetflix(action.title)
        }
    }

    /** The banner's "Stremio" or "Resume" (Stremio), or its "Netflix". */
    private fun play(action: CardAction) {
        when (action) {
            is CardAction.OpenTitle -> handOff = HandOff(action.title, TitleApp.Stremio)
            is CardAction.OpenVideo -> openVideo(action)
            is CardAction.OpenNetflix -> openNetflix(action.title)
        }
    }

    /**
     * The title's page in Netflix, through the hand-off with Netflix's name (25 Oct 2026).
     * The button may have been drawn from an older answer, so the answer is checked again first.
     */
    private fun openNetflix(title: Title) {
        val checked = NetflixRules.withId(title, app.home.state.value.netflix, System.currentTimeMillis(), signInId)
        if (checked.netflixId == null) {
            // The answer no longer counts: draw the buttons again, without Netflix's.
            netflixChecks++
            return
        }
        handOff = HandOff(checked, TitleApp.Netflix)
    }

    /** [title], given to a page earlier, with its Netflix id as it is now. */
    @Composable
    private fun withNetflixNow(title: Title, homeState: HomeState): Title =
        remember(title, homeState, signInId, netflixChecks) { NetflixRules.withId(title, homeState.netflix, System.currentTimeMillis(), signInId) }

    private var barRequests = 0

    /** Moves focus to [item] in the top bar. */
    private fun askBar(item: BarItem) {
        barRequest = BarRequest(item, ++barRequests)
    }

    /** Focus on a tab shows its page under the bar; focus stays in the bar. */
    private fun showTab(next: Tab) {
        if (next == tab) return
        if (next == Tab.Apps) {
            appsTakesFocus = false
            appsStartOnLauncher = false
        }
        tab = next
    }

    /** Back on the bar, away from Home: the Home tab. */
    private fun showHomeTabInBar() {
        tab = Tab.Home
        askBar(BarItem.OfTab(Tab.Home))
    }

    /** All Apps, as the Apps tab, with focus on its first tile, or on Personal TV Launcher after its Settings. */
    private fun openApps(startOnLauncher: Boolean = false) {
        appsTakesFocus = true
        appsStartOnLauncher = startOnLauncher
        tab = Tab.Apps
    }

    /** Down from the bar: into the page, where focus was last. */
    private fun enterPage() {
        when (tab) {
            Tab.Home, Tab.Movies, Tab.Shows -> homeFocusReturns++
            Tab.Apps -> appsEntries++
        }
    }

    private fun pressBarButton(button: BarButton) {
        when (button) {
            // Google's assistant opens over the launcher, which stays under it.
            BarButton.Assistant -> launch(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            BarButton.Screensaver -> screensaver = true
            BarButton.Settings -> screen = Screen.Settings(fromBar = true)
        }
    }

    private fun closeScreensaver() {
        if (!screensaver) return
        screensaver = false
        askBar(BarItem.OfButton(BarButton.Screensaver))
    }

    private fun openMenu(title: Title) {
        menu = title
        menuGeneration = app.session.generation.value
        val changes = app.titleChanges
        menuStatus = changes.sending.value[changes.key(title, menuGeneration)]?.let(TitleMenuStatus::Sending) ?: TitleMenuStatus.Ready
    }

    /**
     * OK on a menu row: a second OK for a change that cannot be taken back, then the change. It is
     * sent by the app ([TitleChanges]), so it finishes if the menu or the activity goes first, and
     * a change already sending for this title is not sent again. Its answer arrives in
     * [showChangeResult].
     */
    private fun chooseInMenu(title: Title, change: TitleChange) {
        val changes = app.titleChanges
        if (changes.key(title, menuGeneration) in changes.sending.value) return
        val next = menuStatus.next(change) ?: return
        menuStatus = next
        if (next is TitleMenuStatus.Sending) changes.send(title, change, menuGeneration)
    }

    /**
     * A change's answer, for the menu only while it shows the same title in the same sign-in: it
     * closes when Trakt confirms. Otherwise the row says why it stays: nothing to remove, or a
     * failure it offers to retry.
     */
    private fun showChangeResult(result: TitleChanges.Result) {
        val shown = menu ?: return
        if (app.titleChanges.key(shown, menuGeneration) != result.key) return
        menuStatus = when (result.result) {
            ChangeResult.Done -> return closeMenu()
            ChangeResult.NothingToRemove -> TitleMenuStatus.NothingToRemove(result.change)
            ChangeResult.Failed -> TitleMenuStatus.Failed(result.change)
        }
    }

    private fun closeMenu() {
        menu = null
        menuStatus = TitleMenuStatus.Ready
        homeFocusReturns++
    }

    private fun closeDetails() {
        details = null
        homeFocusReturns++
    }

    /** The hand-off closes. Over Home, Home gives focus back to the card; the details page takes it itself. */
    private fun endHandOff() {
        handOff = null
        if (details == null) homeFocusReturns++
    }

    private fun openVideo(action: CardAction.OpenVideo) {
        runCatching { Intent.parseUri(action.intentUri, Intent.URI_INTENT_SCHEME) }.getOrNull()?.let(::launch)
    }

    private fun openApp(app: AppTile) {
        app.packageName?.let(::openPackage)
    }

    private fun openPackage(packageName: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: return
        launch(intent)
    }

    /** The TV's screensaver settings, where the launcher's screensaver is chosen; system Settings if the TV has none. */
    private fun openScreensaverSettings() {
        if (!launch(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) launch(SystemRoutes.settings())
    }

    /** False when no app can open [intent]. */
    private fun launch(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        // The app went away after the intent was built. The tile stays silent.
        false
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** A title on its way to [app], shown by the hand-off screen until the app is in front. */
private data class HandOff(val title: Title, val app: TitleApp)
