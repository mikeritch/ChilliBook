package uk.chillibook.diary

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class UpdateBackupTest {
    @Test fun replacesOnlyWithVerifiedDiaryAndClearsBothFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "update-backup-test").toFile()
        try {
            val backup = UpdateBackup(directory)
            val date = "2026-11-15"
            val original = Diary(
                days = listOf(Day(date, work = false, caffeineTimes = emptySet())),
                checks = listOf(Check(date, "am", symptoms = mapOf("Headache" to UNRATED_SYMPTOM))),
                prefs = Prefs(theme = "dark", morningReminder = 420)
            )
            assertNull(backup.modifiedAt())
            backup.write(original)
            assertNotNull(backup.modifiedAt())
            assertEquals(original, backup.read())
            assertEquals(1, directory.listFiles()?.size)

            val pending = File(directory, "pre-update-diary.json.tmp")
            pending.writeText("Interrupted write")
            assertEquals(original, backup.read())
            val invalid = original.copy(checks = listOf(Check(date, "am", energy = 99)))
            assertTrue(runCatching { backup.write(invalid) }.isFailure)
            assertEquals(original, backup.read())
            assertEquals(1, directory.listFiles()?.size)

            val replacement = original.copy(prefs = original.prefs.copy(eveningReminder = 1230))
            backup.write(replacement)
            assertEquals(replacement, UpdateBackup(directory).read())
            assertEquals(1, directory.listFiles()?.size)

            pending.writeText("Interrupted write")
            backup.clear()
            assertNull(backup.modifiedAt())
            assertTrue(runCatching { backup.read() }.isFailure)
            assertEquals(0, directory.listFiles()?.size)
        } finally {
            directory.deleteRecursively()
        }
    }
}
