package app.opentvlauncher.scripts

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * scripts/tv-settings.sh saves the settings that the launcher's setup changes, and puts them back.
 * A fake adb here keeps the TV's settings in files, as the TV's own settings command does.
 */
class TvSettingsScriptTest {
    private val script = listOf(File("../scripts/tv-settings.sh"), File("scripts/tv-settings.sh")).first { it.exists() }

    private val dir = Files.createTempDirectory("tv-settings").toFile()
    private val tv = File(dir, "tv").apply { mkdirs() }
    private val calls = File(dir, "calls")
    private val backup = File(dir, "before.txt")

    init {
        // It reads its standard input, as adb does, so a script that gives it the backup file loses it.
        File(dir, "adb").apply {
            writeText(
                """
                |#!/usr/bin/env bash
                |shift 3
                |cat >/dev/null
                |eval "set -- $*"
                |echo "$*" >>"${calls.path}"
                |case "$1 $2" in
                |    "settings get") cat "${tv.path}/$4" 2>/dev/null || echo null ;;
                |    "settings put") printf '%s' "$5" >"${tv.path}/$4" ;;
                |    "settings delete") rm -f "${tv.path}/$4" ;;
                |    "cmd package") printf 'priority=0 preferredOrder=0 match=0x108000\n%s/.Main\n' "$(cat "${tv.path}/home")" ;;
                |    "cmd role") printf '%s' "${'$'}{@: -1}" >"${tv.path}/home" ;;
                |esac
                """.trimMargin(),
            )
            setExecutable(true)
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun run(vararg args: String): Pair<Int, String> {
        val builder = ProcessBuilder("bash", script.path, *args).redirectErrorStream(true)
        builder.environment()["PATH"] = dir.path + File.pathSeparator + System.getenv("PATH")
        val process = builder.start()
        val out = process.inputStream.bufferedReader().readText()
        return process.waitFor() to out
    }

    private fun set(key: String, value: String) = File(tv, key).writeText(value)

    private fun get(key: String) = File(tv, key).takeIf { it.exists() }?.readText()

    private fun before() {
        set("screensaver_components", "com.google.android.apps.tv.dreamx/.service.Backdrop")
        set("enabled_accessibility_services", TALKBACK)
        set("accessibility_enabled", "1")
        set("home", GOOGLE_HOME)
    }

    /** What the setup changes: the screensaver, the Home override and, by hand, Home. */
    private fun setUp() {
        set("screensaver_components", "app.opentvlauncher/app.opentvlauncher.screensaver.ScreensaverService")
        set("enabled_accessibility_services", "$TALKBACK:app.opentvlauncher/app.opentvlauncher.system.HomeKeyService")
        set("home", "com.example.otherhome")
    }

    @Test
    fun restorePutsBackEachSettingThatTheBackupSaved() {
        before()
        assertEquals(0, run("backup", "tv-serial", backup.path).first)
        setUp()
        val (code, out) = run("restore", "tv-serial", backup.path)
        assertEquals(out, 0, code)
        assertEquals("com.google.android.apps.tv.dreamx/.service.Backdrop", get("screensaver_components"))
        assertEquals(TALKBACK, get("enabled_accessibility_services"))
        assertEquals("1", get("accessibility_enabled"))
        assertEquals(GOOGLE_HOME, get("home"))
    }

    @Test
    fun aSettingThatWasNotSetIsDeletedAgain() {
        set("home", GOOGLE_HOME)
        run("backup", "tv-serial", backup.path)
        setUp()
        assertEquals(0, run("restore", "tv-serial", backup.path).first)
        assertEquals(null, get("screensaver_components"))
        assertEquals(null, get("enabled_accessibility_services"))
    }

    @Test
    fun homeIsLeftAloneWhenItIsAlreadyTheSavedApp() {
        before()
        run("backup", "tv-serial", backup.path)
        calls.delete()
        run("restore", "tv-serial", backup.path)
        assertFalse(calls.readText().contains("cmd role"))
    }

    @Test
    fun theBackupHoldsNoSerialAndNeverReplacesAnEarlierOne() {
        before()
        run("backup", "tv-serial", backup.path)
        assertFalse(backup.readText().contains("tv-serial"))
        set("screensaver_components", "changed/.Later")
        val (code, _) = run("backup", "tv-serial", backup.path)
        assertNotEquals(0, code)
        assertTrue(backup.readText().contains("dreamx/.service.Backdrop"))
    }

    @Test
    fun aLineThatIsNotASettingIsSkipped() {
        before()
        backup.writeText("secure screensaver_components 'x; reboot'\nother line\nsecure accessibility_enabled 0\n")
        val (code, out) = run("restore", "tv-serial", backup.path)
        assertEquals(0, code)
        assertTrue(out.contains("Skipped"))
        assertEquals("com.google.android.apps.tv.dreamx/.service.Backdrop", get("screensaver_components"))
        assertEquals("0", get("accessibility_enabled"))
    }

    private companion object {
        const val TALKBACK = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        const val GOOGLE_HOME = "com.google.android.apps.tv.launcherx"
    }
}
