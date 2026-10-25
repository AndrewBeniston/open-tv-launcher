package app.opentvlauncher.ui.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * The tests' sample artwork, drawn in code from the CSS placeholders in the design's wireframes,
 * so the screen draws without the network. The app itself never has it.
 */
enum class SampleArt(override val tint: Color, private val paint: DrawScope.() -> Unit) : PaintedArt {
    TalesOfTheQuietCoast(Color(0x4D2C5C56), {
        radialFill(0.72f, 0.40f, 0.70f, 0.90f, 0f to Color(0xFF2C5852), 0.48f to Color(0xFF15302D), 0.85f to Color(0xFF0A1514))
        glow(0.74f, 0.30f, 0.20f, 0.32f, Color(0x8CCDE8DE))
        glow(0.70f, 0.96f, 0.70f, 0.40f, Color(0x5978A096))
        if (size.width / size.height > 2f) {
            trunks(
                listOf(
                    Trunk(0.40f, 0.014f, 0.7f), Trunk(0.49f, 0.026f, 0.95f), Trunk(0.56f, 0.011f, 0.8f),
                    Trunk(0.63f, 0.032f, 1f), Trunk(0.78f, 0.016f, 0.85f), Trunk(0.86f, 0.024f, 0.95f),
                    Trunk(0.94f, 0.012f, 0.7f),
                ),
            )
            beam(0.58f, 0.34f)
        } else {
            trunks(listOf(Trunk(0.20f, 0.03f, 0.9f), Trunk(0.44f, 0.04f, 1f), Trunk(0.70f, 0.024f, 0.85f), Trunk(0.84f, 0.05f, 1f)))
            beam(0.30f, 0.60f)
        }
    }),

    HarbourLights(Color(0x4D2C4A8C), {
        vertical(0f to Color(0xFF0A1634), 0.58f to Color(0xFF15295A), 0.70f to Color(0xFF22325C), 0.78f to Color(0xFF0B0F1C), 1f to Color(0xFF05070D))
        glow(0.64f, 0.26f, 0.26f, 0.34f, Color(0x668CB4FF))
        glow(0.64f, 0.26f, 0.03f, 0.06f, Color(0xFFF4F8FF), fade = 0.8f)
        val r = box(0.624f, 0.28f, 0.032f, 0.45f)
        polygon(
            r,
            listOf(0.42f to 0f, 0.58f to 0f, 1f to 1f, 0f to 1f),
            Brush.verticalGradient(0f to Color(0x80BED7FF), 1f to Color(0x0FBED7FF), startY = r.top, endY = r.bottom),
        )
    }),

    KitchenCouncil(Color(0x4D82664F), {
        horizontal(0f to Color(0xFF5D4635), 0.45f to Color(0xFF82664F), 0.80f to Color(0xFF6A523F), 1f to Color(0xFF4C3A2D))
        glow(0.18f, 0.28f, 0.16f, 0.26f, Color(0x9EFFD28C))
        val window = box(0.58f, 0.10f, 0.24f, 0.40f)
        drawRoundRect(
            Brush.verticalGradient(0f to Color(0x5CE8DECC), 1f to Color(0x24E8DECC), startY = window.top, endY = window.bottom),
            topLeft = window.topLeft,
            size = window.size,
            cornerRadius = CornerRadius(window.width * 0.02f),
        )
        val couch = box(0.26f, 0.60f, 0.68f, 0.52f)
        drawRoundRect(
            Brush.verticalGradient(0f to Color(0xFF3D2B20), 1f to Color(0xFF261A13), startY = couch.top, endY = couch.bottom),
            topLeft = couch.topLeft,
            size = couch.size,
            cornerRadius = CornerRadius(couch.width * 0.14f, couch.height * 0.34f),
        )
    }),

    Glacier(Color(0x4D8A4A2C), {
        vertical(0f to Color(0xFF6A6B6C), 0.55f to Color(0xFF47443F), 1f to Color(0xFF231713))
        glow(0.52f, 0.24f, 0.28f, 0.36f, Color(0x9ECDCAC4))
        glow(0.50f, 0.74f, 0.42f, 0.30f, Color(0x8CFF7630))
        val r = box(0f, 0.54f, 1f, 0.46f)
        polygon(
            r,
            listOf(0f to 1f, 0f to 0.72f, 0.30f to 0.42f, 0.44f to 0.12f, 0.56f to 0.12f, 0.70f to 0.44f, 1f to 0.66f, 1f to 1f),
            Brush.verticalGradient(0f to Color(0xFF2C1D17), 1f to Color(0xFF120B09), startY = r.top, endY = r.bottom),
        )
    }),

    Fern(Color(0x3D627A66), {
        vertical(0f to Color(0xFF1C2622), 0.60f to Color(0xFF0E1613), 1f to Color(0xFF060908))
        glow(0.50f, 0.58f, 0.24f, 0.30f, Color(0x52829884))
        glow(0.47f, 0.72f, 0.024f, 0.044f, Color(0xFFE8BD3C), fade = 0.8f)
        trunks(listOf(Trunk(0.14f, 0.04f, 0.95f), Trunk(0.30f, 0.024f, 0.8f), Trunk(0.66f, 0.03f, 0.9f), Trunk(0.82f, 0.05f, 1f)))
    }),

    SlowWeekend(Color(0x4D607828), {
        radialFill(0.80f, 0.30f, 0.85f, 1f, 0f to Color(0xFF46632A), 0.50f to Color(0xFF21351A), 0.90f to Color(0xFF0D160A))
        glow(0.76f, 0.36f, 0.32f, 0.44f, Color(0x80B0C460), fade = 0.72f)
        glow(0.58f, 0.26f, 0.06f, 0.10f, Color(0xE6FFF2C4), fade = 0.72f)
        glow(0.66f, 0.46f, 0.045f, 0.07f, Color(0xBFFFEAAC), fade = 0.72f)
        glow(0.80f, 0.18f, 0.08f, 0.11f, Color(0xA8FAEEB6), fade = 0.72f)
        glow(0.88f, 0.46f, 0.05f, 0.08f, Color(0x99F4E6A4), fade = 0.72f)
        glow(0.73f, 0.64f, 0.035f, 0.06f, Color(0x8CFFEEB6), fade = 0.72f)
        glow(0.94f, 0.28f, 0.04f, 0.06f, Color(0x80FFEEB6), fade = 0.72f)
    }),

    Ember(Color(0x29787878), {
        vertical(0f to Color(0xFF3A3A3A), 0.60f to Color(0xFF1E1E1E), 1f to Color(0xFF0D0D0D))
        glow(0.70f, 0.34f, 0.40f, 0.50f, Color(0x47C8C8C8))
        glow(0.72f, 0.30f, 0.10f, 0.16f, Color(0xB3F0ECDC))
        dots(23.dp, 0.9.dp, Color(0x57FFFFFF))
        dots(15.dp, 0.7.dp, Color(0x2EFFFFFF), offsetX = 11.dp, offsetY = 6.dp)
    }),

    Orbit(Color(0x4D961414), {
        vertical(0f to Color(0xFF2A0606), 0.50f to Color(0xFF4A0B0B), 1f to Color(0xFF120303))
        glow(0.70f, 0.40f, 0.60f, 0.50f, Color(0x99961414), fade = 0.72f)
        glow(0.70f, 0.38f, 0.18f, 0.30f, Color(0xD9FF4632), fade = 0.72f)
        val city = box(0f, 0.42f, 1f, 0.58f)
        polygon(
            city,
            listOf(
                0f to 1f, 0f to 0.44f, 0.06f to 0.44f, 0.06f to 0.30f, 0.11f to 0.30f, 0.11f to 0.52f, 0.17f to 0.52f,
                0.17f to 0.20f, 0.21f to 0.20f, 0.21f to 0.08f, 0.24f to 0.08f, 0.24f to 0.36f, 0.31f to 0.36f,
                0.31f to 0.48f, 0.37f to 0.48f, 0.37f to 0.26f, 0.42f to 0.26f, 0.42f to 0.40f, 0.49f to 0.40f,
                0.49f to 0.14f, 0.53f to 0.14f, 0.53f to 0.34f, 0.60f to 0.34f, 0.60f to 0.50f, 0.66f to 0.50f,
                0.66f to 0.22f, 0.70f to 0.22f, 0.70f to 0.04f, 0.73f to 0.04f, 0.73f to 0.30f, 0.80f to 0.30f,
                0.80f to 0.46f, 0.86f to 0.46f, 0.86f to 0.18f, 0.91f to 0.18f, 0.91f to 0.38f, 1f to 0.38f, 1f to 1f,
            ),
            Brush.verticalGradient(0f to Color(0xFF0D0505), 1f to Color(0xFF050202), startY = city.top, endY = city.bottom),
        )
        listOf(0.86f to 1f, 0.90f to 0.6f, 0.94f to 0.4f).forEach { (y, a) ->
            drawRect(
                Brush.horizontalGradient(0f to Color(0x00FF3C28), 0.4f to Color(0xE6FF3C28), 0.6f to Color(0xF2FFC8AA), 1f to Color(0x00FFC8AA)),
                topLeft = box(0f, y, 1f, 0.006f).topLeft,
                size = box(0f, y, 1f, 0.006f).size,
                alpha = a,
            )
        }
    }),

    PosterEmber(Color(0x29787878), {
        vertical(0f to Color(0xFF4A4A4A), 1f to Color(0xFF151515))
        dots(15.dp, 0.9.dp, Color(0x80FFFFFF))
    }),
    PosterGoAndFetch(Color(0x3D78683C), {
        vertical(0f to Color(0xFF3E3A26), 1f to Color(0xFF151309))
        glow(0.50f, 0.58f, 0.40f, 0.30f, Color(0xFF8A7A52))
    }),
    PosterRedcap(Color(0x3D5B2A6E), {
        vertical(0f to Color(0xFF123B2C), 1f to Color(0xFF0A1410))
        glow(0.50f, 0.60f, 0.50f, 0.40f, Color(0xFF5B2A6E))
    }),
    PosterOrbit(Color(0x3DE23A2A), {
        vertical(0f to Color(0xFF5A0D0D), 1f to Color(0xFF1C0505))
        glow(0.50f, 0.70f, 0.60f, 0.40f, Color(0xFFE23A2A))
    }),
    PosterQuestionOfSalt(Color(0x3D8FB7E0), {
        vertical(0f to Color(0xFFE8A6B8), 0.60f to Color(0xFF8FB7E0), 1f to Color(0xFF2C4C7A))
    }),
    PosterHollowby(Color(0x3D6F8796), {
        vertical(0f to Color(0xFF6F8796), 1f to Color(0xFF2B3A44))
        glow(0.50f, 0.70f, 0.50f, 0.30f, Color(0xFFC9D6DE))
    }),
    PosterWestmoor(Color(0x3D7A8A68), {
        vertical(0f to Color(0xFFA9B59A), 0.70f to Color(0xFF4D5A46), 1f to Color(0xFF2A3326))
        drawRect(Color(0x80F0F0E8), topLeft = box(0.46f, 0f, 0.08f, 1f).topLeft, size = box(0.46f, 0f, 0.08f, 1f).size)
    }),
    PosterSlowWeekend(Color(0x3D607828), {
        vertical(0f to Color(0xFF46632A), 1f to Color(0xFF152410))
        glow(0.40f, 0.30f, 0.14f, 0.10f, Color(0xCCFFF0BE), fade = 0.72f)
        glow(0.66f, 0.46f, 0.10f, 0.08f, Color(0x99FFEAAC), fade = 0.72f)
    }),
    PosterRiverHours(Color(0x3D8C7A5A), {
        vertical(0f to Color(0xFF6B5E48), 1f to Color(0xFF1E1A14))
        glow(0.50f, 0.36f, 0.40f, 0.20f, Color(0xFFD8C8A0))
    }),
    PosterWatcher(Color(0x3D5C6B3A), {
        vertical(0f to Color(0xFF3A4228), 1f to Color(0xFF0F120A))
        glow(0.50f, 0.80f, 0.60f, 0.30f, Color(0xFF8C9A5A))
    }),
    PosterEightSailors(Color(0x29787878), {
        vertical(0f to Color(0xFF5A5A5A), 1f to Color(0xFF101010))
        glow(0.50f, 0.50f, 0.50f, 0.40f, Color(0xFF9A9A9A))
    }),
    PosterOnTheRoad(Color(0x3DB4283C), {
        vertical(0f to Color(0xFF6E1420), 1f to Color(0xFF1E0508))
        glow(0.60f, 0.40f, 0.30f, 0.30f, Color(0xFFE0A050))
    }),
    PosterCarriedAlong(Color(0x3D3C8CA0), {
        vertical(0f to Color(0xFF6CB4C8), 0.60f to Color(0xFF2C6478), 1f to Color(0xFF102830))
        glow(0.50f, 0.70f, 0.40f, 0.20f, Color(0xFFF0C878))
    }),
    PosterCorvaVesta(Color(0x3DC8503C), {
        vertical(0f to Color(0xFFE07850), 0.60f to Color(0xFF8C2C3C), 1f to Color(0xFF2A0C14))
    }),
    PosterFifthBay(Color(0x29606060), {
        vertical(0f to Color(0xFF2A2A2A), 1f to Color(0xFF050505))
        glow(0.70f, 0.20f, 0.30f, 0.30f, Color(0xFFBEBEBE))
    }),
    PosterSketchOfABoat(Color(0x3DD0703C), {
        vertical(0f to Color(0xFF2C4C5C), 0.70f to Color(0xFF1A2C36), 1f to Color(0xFF0A1218))
        glow(0.50f, 0.60f, 0.14f, 0.12f, Color(0xFFF08C3C), fade = 0.72f)
    }),

    YouTubeEffects(Color(0x3DB42A8C), {
        diagonal(Color(0xFF1A0B2E), Color(0xFF3B0F4F))
        glow(0.76f, 0.45f, 0.40f, 0.70f, Color(0xFFFF3DB4))
    }),
    YouTubeBlueScreen(Color(0x3D27B452), {
        diagonal(Color(0xFF27B452), Color(0xFF0E6C30))
        glow(0.78f, 0.60f, 0.30f, 0.60f, Color(0xFF0A3A1A))
    }),
    YouTubeShed(Color(0x3DC4613F), {
        diagonal(Color(0xFFC4613F), Color(0xFF7A3220))
        glow(0.82f, 0.50f, 0.50f, 0.70f, Color(0xFFF2A47C))
    }),
    YouTubeTechNews(Color(0x3D3A62D8), {
        diagonal(Color(0xFF0B1430), Color(0xFF1B2D6B))
        glow(0.80f, 0.40f, 0.40f, 0.60f, Color(0xFF3A62D8))
    }),
    YouTubeWeekly(Color(0x3D1D4FAE), {
        diagonal(Color(0xFF061026), Color(0xFF0C2453))
        glow(0.70f, 0.60f, 0.60f, 0.70f, Color(0xFF1D4FAE), fade = 0.72f)
        dots(7.dp, 1.dp, Color(0x8C6EAAFF))
    }),
    ;

    override fun draw(scope: DrawScope) = scope.paint()

    override val thumbLines: List<ThumbLine>
        get() = when (this) {
            YouTubeEffects -> listOf(ThumbLine("Effects Team Talk", 11, Color(0xFFFFD84A)), ThumbLine("Shots 12", 40))
            YouTubeBlueScreen -> listOf(ThumbLine("Green Screen", 26), ThumbLine("Processing", 15, Color(0xFF111111), badge = Color(0xFFFFD84A)))
            YouTubeShed -> listOf(ThumbLine("Shed", 30), ThumbLine("With a Friend", 20, Color(0xFFFBE3D4)))
            YouTubeTechNews -> listOf(ThumbLine("Tech News", 36, Color(0xFFFFD84A)), ThumbLine("Model 7 Loom", 15))
            YouTubeWeekly -> listOf(ThumbLine("Tech Weekly", 13, Color(0xFFCFE1FF)), ThumbLine("v7.0", 44))
            else -> emptyList()
        }

    override val posterTitleSize: Int?
        get() = when (this) {
            PosterEmber -> 20
            PosterGoAndFetch -> 15
            PosterHollowby -> 16
            PosterRedcap -> 18
            PosterOrbit -> 22
            PosterWestmoor -> 16
            PosterQuestionOfSalt -> 11
            PosterSlowWeekend -> 15
            else -> null
        }
}
