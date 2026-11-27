package uk.chillibook.diary

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class UpdateBackup(directory: File) {
    constructor(context: Context) : this(context.noBackupFilesDir)

    private val snapshot = File(directory, "pre-update-diary.json")
    private val pending = File(directory, "pre-update-diary.json.tmp")

    fun write(diary: Diary) {
        synchronized(lock) {
            try {
                val encoded = Backup.encode(diary)
                FileOutputStream(pending).use { output ->
                    output.write(encoded.toByteArray(Charsets.UTF_8))
                    output.fd.sync()
                }
                check(decode(pending) == diary) { "Update backup does not match the diary" }
                Files.move(pending.toPath(), snapshot.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } finally {
                pending.delete()
            }
        }
    }

    fun read(): Diary = synchronized(lock) { decode(snapshot) }

    fun modifiedAt(): Long? = synchronized(lock) {
        snapshot.lastModified().takeIf { it > 0L }
    }

    fun clear() {
        synchronized(lock) {
            Files.deleteIfExists(pending.toPath())
            Files.deleteIfExists(snapshot.toPath())
        }
    }

    private fun decode(file: File): Diary = file.bufferedReader().use(Backup::decode)

    private companion object {
        val lock = Any()
    }
}
