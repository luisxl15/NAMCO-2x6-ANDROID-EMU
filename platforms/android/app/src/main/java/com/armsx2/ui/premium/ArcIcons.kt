package com.armsx2.ui.premium

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.armsx2.R

/**
 * The launcher's icon set: real vector assets on a 24dp grid, drawn to one weight.
 *
 * They replace the Unicode glyphs the UI used to set as text. A glyph is whatever the device's
 * font happens to have — it arrives at a different weight and optical size than the icon beside
 * it, shifts baseline against its label, and on a font without the codepoint it is simply a box.
 * These are drawn once, tint with the text colour, and line up with each other.
 */
object Arc {
    // Chrome
    @DrawableRes val menu = R.drawable.ic_arc_menu
    @DrawableRes val settings = R.drawable.ic_arc_settings
    @DrawableRes val back = R.drawable.ic_arc_back
    @DrawableRes val chevronLeft = R.drawable.ic_arc_chevron_left
    @DrawableRes val chevronRight = R.drawable.ic_arc_chevron_right
    @DrawableRes val chevronDown = R.drawable.ic_arc_chevron_down
    @DrawableRes val chevronUp = R.drawable.ic_arc_chevron_up
    @DrawableRes val close = R.drawable.ic_arc_close
    @DrawableRes val search = R.drawable.ic_arc_search
    @DrawableRes val reset = R.drawable.ic_arc_reset
    @DrawableRes val arrowUp = R.drawable.ic_arc_arrow_up
    @DrawableRes val external = R.drawable.ic_arc_external
    @DrawableRes val more = R.drawable.ic_arc_more
    @DrawableRes val check = R.drawable.ic_arc_check
    @DrawableRes val info = R.drawable.ic_arc_info

    // Library and content
    @DrawableRes val library = R.drawable.ic_arc_library
    @DrawableRes val play = R.drawable.ic_arc_play
    @DrawableRes val memcard = R.drawable.ic_arc_memcard
    @DrawableRes val bios = R.drawable.ic_arc_bios
    @DrawableRes val controls = R.drawable.ic_arc_controls
    @DrawableRes val saves = R.drawable.ic_arc_saves
    @DrawableRes val artwork = R.drawable.ic_arc_artwork
    @DrawableRes val patches = R.drawable.ic_arc_patches
    @DrawableRes val textures = R.drawable.ic_arc_textures
    @DrawableRes val language = R.drawable.ic_arc_language
    @DrawableRes val star = R.drawable.ic_arc_star
    @DrawableRes val starOff = R.drawable.ic_arc_star_off

    // Settings tabs and the in-game menu
    @DrawableRes val display = R.drawable.ic_arc_display
    @DrawableRes val audio = R.drawable.ic_arc_audio
    @DrawableRes val performance = R.drawable.ic_arc_bolt
    @DrawableRes val fixes = R.drawable.ic_arc_fixes
    @DrawableRes val hotkeys = R.drawable.ic_arc_hotkeys
    @DrawableRes val overlay = R.drawable.ic_arc_overlay
    @DrawableRes val move = R.drawable.ic_arc_move
    @DrawableRes val fastForward = R.drawable.ic_arc_forward
    @DrawableRes val stop = R.drawable.ic_arc_stop
    @DrawableRes val eject = R.drawable.ic_arc_eject
    @DrawableRes val backspace = R.drawable.ic_arc_backspace
    @DrawableRes val shift = R.drawable.ic_arc_shift
    @DrawableRes val pcsx2 = R.drawable.ic_arc_pcsx2

    // Manager screens
    @DrawableRes val plus = R.drawable.ic_arc_plus
    @DrawableRes val refresh = R.drawable.ic_arc_refresh
    @DrawableRes val importFile = R.drawable.ic_arc_import
    @DrawableRes val export = R.drawable.ic_arc_export
    @DrawableRes val folder = R.drawable.ic_arc_folder
    @DrawableRes val edit = R.drawable.ic_arc_edit
    @DrawableRes val archive = R.drawable.ic_arc_archive
    @DrawableRes val delete = R.drawable.ic_arc_delete
    @DrawableRes val power = R.drawable.ic_arc_power
    @DrawableRes val sort = R.drawable.ic_arc_sort
    @DrawableRes val list = R.drawable.ic_arc_list
    @DrawableRes val shelf = R.drawable.ic_arc_shelf
}

/** Draws one of [Arc]'s icons at [size], tinted like the text it sits with. */
@Composable
fun ArcIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    tint: Color = Palette.label,
    size: Dp = 20.dp,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}
