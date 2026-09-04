package com.armsx2.art

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.armsx2.CustomCovers
import com.armsx2.GameInfo
import java.io.File

/**
 * Wide "hero" artwork — the banner that fills the featured card behind the title.
 *
 * Separate from [CustomCovers] because it is a different picture, not a different size of the
 * same one: a cover is a ~2:3 box front, a hero is a ~16:6 key-art strip built to be cropped
 * and have text laid over it. Using a cover there means either pillarboxing it or blowing it up
 * until it is a blur, which is why the card carried a faint logo watermark instead.
 *
 * Stored beside the covers, keyed the same way, so one game's art all lives together and a
 * backup picks it up with everything else.
 */
object HeroArt {
    /** Bumped on every write so Compose reloads the file it already has a handle to. */
    val version = mutableIntStateOf(0)

    private fun dir(context: Context): File = File(CustomCovers.coversRoot(context), "hero")

    private fun key(game: GameInfo): String =
        (game.serial?.takeIf { it.isNotBlank() } ?: game.title)
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")

    fun fileFor(context: Context, game: GameInfo): File? =
        File(dir(context), key(game) + ".png").takeIf { it.isFile && it.length() > 0L }

    fun setBytes(context: Context, game: GameInfo, bytes: ByteArray): Boolean = runCatching {
        if (bytes.isEmpty()) return false
        val target = File(dir(context), key(game) + ".png")
        target.parentFile?.mkdirs()
        // Staged and renamed, for the same reason as CustomCovers.setBytes: the version bump
        // recomposes immediately, and a reader must never catch a half-written file.
        val staging = File(target.parentFile, target.name + ".part")
        staging.outputStream().use { it.write(bytes) }
        if (!staging.renameTo(target)) {
            staging.copyTo(target, overwrite = true)
            staging.delete()
        }
        version.intValue++
        true
    }.getOrDefault(false)

    fun remove(context: Context, game: GameInfo): Boolean = runCatching {
        val gone = File(dir(context), key(game) + ".png").delete()
        if (gone) version.intValue++
        gone
    }.getOrDefault(false)
}
