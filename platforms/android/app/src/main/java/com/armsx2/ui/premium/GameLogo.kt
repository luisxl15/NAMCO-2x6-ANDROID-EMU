package com.armsx2.ui.premium

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.armsx2.art.ArcadeMedia
import com.armsx2.GameInfo
import com.armsx2.EnglishTitles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A game's logo from the media repository, or [fallback] when there is none.
 *
 * Fetched on first use and cached; the pack covers about 35 of the ids the compatibility list
 * knows, so [fallback] is a normal outcome and not an error path. Left-aligned and sized by
 * HEIGHT, because the logos are one canvas size (1920x800) with the artwork floating inside it at
 * whatever width the wordmark happens to be -- fitting them to a width would make a short logo
 * enormous and a long one tiny.
 */
@Composable
fun GameLogo(
    game: GameInfo,
    height: Dp,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val serial = game.serial
    var logo by remember(serial) { mutableStateOf<File?>(null) }
    var settled by remember(serial) { mutableStateOf(false) }

    LaunchedEffect(serial) {
        if (!ArcadeMedia.hasLogo(serial)) {
            settled = true
            return@LaunchedEffect
        }
        logo = withContext(Dispatchers.IO) {
            runCatching { ArcadeMedia.logo(context, serial) }.getOrNull()
        }
        settled = true
    }

    val file = logo
    if (file == null) {
        // Nothing at all until the lookup has settled: flashing the title for a frame and then
        // replacing it with the logo is worse than a beat of nothing.
        if (settled) fallback()
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(file)
            .memoryCacheKey(fileCacheKey(file))
            .diskCacheKey(fileCacheKey(file))
            .crossfade(true)
            .build(),
        contentDescription = game.displayTitle(EnglishTitles.enabled.value),
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = Modifier.height(height).fillMaxWidth(),
    )
}
