package app.opentvlauncher.data

import app.opentvlauncher.ui.art.SampleArt

/**
 * Fixed sample data for the tests, with made-up titles. Release builds have no sample data. Top
 * Picks stands in for Trakt's recommendations, and From Your Watchlist for the watchlist.
 */
object SampleHome {
    const val YOUTUBE_PACKAGE = "com.google.android.youtube.tv"
    const val STREMIO_PACKAGE = "com.stremio.one"
    const val NETFLIX_PACKAGE = "com.netflix.ninja"
    const val IPLAYER_PACKAGE = "bbc.iplayer.android"

    val content = HomeContent(
        slides = listOf(
            BannerSlide(
                key = "sample:tales-of-the-quiet-coast",
                kind = SlideKind.ContinueWatching,
                eyebrow = "Continue Watching",
                title = "Tales of the Quiet Coast",
                meta = "Season 1, Episode 2, Paused 21 Oct",
                synopsis = "A documentary series about unsolved cases.",
                art = Art(SampleArt.TalesOfTheQuietCoast),
                progress = 0.03f,
            ),
            BannerSlide(
                key = "sample:harbour-lights",
                kind = SlideKind.ContinueWatching,
                eyebrow = "Continue Watching",
                title = "Harbour Lights",
                meta = "Film, 53% Watched",
                synopsis = "People learn that other life exists.",
                art = Art(SampleArt.HarbourLights),
                progress = 0.53f,
            ),
            BannerSlide(
                key = "sample:kitchen-council",
                kind = SlideKind.ContinueWatching,
                eyebrow = "Continue Watching",
                title = "Kitchen Council",
                meta = "Season 5, Episode 6, 47% Watched",
                synopsis = "Two neighbours share one kitchen.",
                art = Art(SampleArt.KitchenCouncil),
                progress = 0.47f,
            ),
            BannerSlide(
                key = "sample:slow-weekend",
                kind = SlideKind.TopPick,
                eyebrow = "Top Pick for You",
                title = "Slow Weekend",
                meta = "Film, 2023, Ada Moreno",
                synopsis = "A ferry worker follows his daily routine.",
                art = Art(SampleArt.SlowWeekend),
            ),
            BannerSlide(
                key = "sample:ember",
                kind = SlideKind.TopPick,
                eyebrow = "From Your Watchlist",
                title = "Ember",
                meta = "Film, 1952, Ivo Lundqvist",
                synopsis = "A town clerk decides how to spend his remaining time.",
                art = Art(SampleArt.Ember),
            ),
            BannerSlide(
                key = "sample:orbit",
                kind = SlideKind.TopPick,
                eyebrow = "Top Pick for You",
                title = "Orbit",
                meta = "Film, 1988, Mira Satomaa",
                synopsis = "A courier gains powers that threaten the old city.",
                art = Art(SampleArt.Orbit),
            ),
        ),
        apps = listOf(
            AppTile(AppKind.YouTube, "YouTube", YOUTUBE_PACKAGE),
            AppTile(AppKind.Stremio, "Stremio", STREMIO_PACKAGE),
            AppTile(AppKind.Netflix, "Netflix", NETFLIX_PACKAGE),
            AppTile(AppKind.IPlayer, "BBC iPlayer", IPLAYER_PACKAGE),
            AppTile(AppKind.AllApps, "All Apps", null),
        ),
        rows = listOf(
            WideRow(
                key = CONTINUE_WATCHING_ROW,
                title = "Continue Watching",
                cards = listOf(
                    WideCard("Tales of the Quiet Coast", "S1 E2 · 3% Watched", Art(SampleArt.TalesOfTheQuietCoast), progress = 0.03f),
                    WideCard("Harbour Lights", "Film · 53% Watched", Art(SampleArt.HarbourLights), progress = 0.53f),
                    WideCard("Kitchen Council", "S5 E6 · 47% Watched", Art(SampleArt.KitchenCouncil), progress = 0.47f),
                    WideCard("The Glacier: Rescue from Skarvika", "Film · 62% Watched", Art(SampleArt.Glacier), progress = 0.62f),
                    WideCard("Fern", "S2 E8 · 11% Watched", Art(SampleArt.Fern), progress = 0.11f),
                ),
            ),
            WideRow(
                title = "Recommended on YouTube",
                cards = listOf(
                    WideCard("Shot Review 12", "Workshop Club", Art(SampleArt.YouTubeEffects)),
                    WideCard("Blue Screen Compositing", "Workshop Club", Art(SampleArt.YouTubeBlueScreen)),
                    WideCard("Building a Shed with a Friend", null, Art(SampleArt.YouTubeShed)),
                    WideCard("Tech News: Sailing Research and Model 7 Loom", null, Art(SampleArt.YouTubeTechNews)),
                    WideCard(
                        "Tech Weekly 9",
                        "ModelDesk",
                        Art(SampleArt.YouTubeWeekly),
                        duration = "26:35",
                    ),
                ),
            ),
            PosterRow(
                title = "Top Picks for You",
                posters = listOf(
                    PosterCard("Ember", "1952 · Drama", Art(SampleArt.PosterEmber)),
                    PosterCard("Go and Fetch", "1985 · War", Art(SampleArt.PosterGoAndFetch)),
                    PosterCard("Hollowby", "2016 · Drama", Art(SampleArt.PosterHollowby)),
                    PosterCard("Redcap", "2003 · Thriller", Art(SampleArt.PosterRedcap)),
                    PosterCard("Orbit", "1988 · Animation", Art(SampleArt.PosterOrbit)),
                    PosterCard("Westmoor", "2017 · Drama", Art(SampleArt.PosterWestmoor)),
                    PosterCard("A Question of Salt and Sea", "1946 · Fantasy", Art(SampleArt.PosterQuestionOfSalt)),
                    PosterCard("Slow Weekend", "2023 · Drama", Art(SampleArt.PosterSlowWeekend)),
                ),
            ),
            PosterRow(
                title = "From Your Watchlist",
                posters = listOf(
                    PosterCard("River Hours", "1953 · Drama", Art(SampleArt.PosterRiverHours)),
                    PosterCard("Watcher", "1979 · Science Fiction", Art(SampleArt.PosterWatcher)),
                    PosterCard("Eight Sailors", "1954 · Action", Art(SampleArt.PosterEightSailors)),
                    PosterCard("On the Road to Sleep", "2000 · Romance", Art(SampleArt.PosterOnTheRoad)),
                    PosterCard("Carried Along", "2001 · Animation", Art(SampleArt.PosterCarriedAlong)),
                    PosterCard("Corva, Vesta", "1984 · Drama", Art(SampleArt.PosterCorvaVesta)),
                    PosterCard("The Fifth Bay", "1949 · Thriller", Art(SampleArt.PosterFifthBay)),
                    PosterCard("Sketch of a Boat in Winter", "2019 · Romance", Art(SampleArt.PosterSketchOfABoat)),
                ),
            ),
        ),
    )
}
