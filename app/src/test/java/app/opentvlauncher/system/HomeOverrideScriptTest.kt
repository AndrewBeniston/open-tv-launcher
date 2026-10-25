package app.opentvlauncher.system

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Review O9: scripts/home-override.sh adds or removes only the launcher's service and keeps every
 * other enabled accessibility service. Its list editing runs here without adb.
 */
class HomeOverrideScriptTest {
    private val script = listOf(File("../scripts/home-override.sh"), File("scripts/home-override.sh")).first { it.exists() }

    /** The list editing for the app id [id], which a builder sets in local.properties. */
    private fun edit(action: String, list: String, id: String = ID): String {
        val builder = ProcessBuilder("bash", script.path, "edit", action, list).redirectErrorStream(true)
        builder.environment()["OPEN_TV_LAUNCHER_ID"] = id
        val process = builder.start()
        val out = process.inputStream.bufferedReader().readText().trim()
        assertEquals(out, 0, process.waitFor())
        return out
    }

    @Test
    fun onAddsTheServiceToWhatIsThere() {
        assertEquals(SERVICE, edit("add", "null"))
        assertEquals("$TALKBACK:$SERVICE", edit("add", TALKBACK))
        // Never twice, in either spelling.
        assertEquals("$TALKBACK:$SERVICE", edit("add", "$TALKBACK:$SERVICE"))
        assertEquals("$TALKBACK:$SERVICE", edit("add", "$TALKBACK:$SERVICE_SHORT"))
    }

    @Test
    fun offRemovesOnlyTheService() {
        assertEquals("$TALKBACK:$MAPPER", edit("remove", "$TALKBACK:$SERVICE:$MAPPER"))
        assertEquals(TALKBACK, edit("remove", "$SERVICE_SHORT:$TALKBACK"))
        assertEquals("", edit("remove", SERVICE))
        assertEquals("", edit("remove", "null"))
        assertEquals(TALKBACK, edit("remove", TALKBACK))
    }

    /**
     * With a builder's own app id, the class keeps its source package, so the service has its full
     * name. The entry from before the package rename (25 Oct 2026) is replaced, not kept.
     */
    @Test
    fun anotherAppIdUsesTheFullClassAndReplacesTheOldName() {
        val own = "com.example.tv/app.opentvlauncher.system.HomeKeyService"
        assertEquals("$TALKBACK:$own", edit("add", TALKBACK, id = "com.example.tv"))
        assertEquals("$TALKBACK:$own", edit("add", "com.example.tv/.system.HomeKeyService:$TALKBACK", id = "com.example.tv"))
        assertEquals("$TALKBACK:$own", edit("add", "com.example.tv/com.example.tv.system.HomeKeyService:$TALKBACK", id = "com.example.tv"))
        // Another app's service of the same name, and an id that differs only where a dot is, stay.
        assertEquals("$SERVICE_SHORT:comXexample.tv/.system.HomeKeyService", edit("remove", "$SERVICE_SHORT:comXexample.tv/.system.HomeKeyService:$own", id = "com.example.tv"))
    }

    private companion object {
        const val ID = "app.opentvlauncher"
        // The script writes the full class name. The TV's own settings can write the short form.
        const val SERVICE = "app.opentvlauncher/app.opentvlauncher.system.HomeKeyService"
        const val SERVICE_SHORT = "app.opentvlauncher/.system.HomeKeyService"
        const val TALKBACK = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        const val MAPPER = "flar2.homebutton/.a11y.HomeButtonService"
    }
}
