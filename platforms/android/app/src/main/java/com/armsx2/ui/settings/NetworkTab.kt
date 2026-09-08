package com.armsx2.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import com.armsx2.config.Settings

/**
 * Rede — hoje, o cabo entre dois gabinetes.
 *
 * Its own tab rather than a section of Advanced, where it first landed. Advanced is game fixes:
 * every other row there is about how one game renders, and a link to another device is not a fix
 * for anything. Anyone looking for it would look for "network" and find "hardware fixes" — which
 * is the kind of place a feature goes to be never found.
 *
 * One section for now. If DEV9's internet side ever gets a screen in this build, it belongs here
 * too, beside this one, rather than scattered across the tabs it happens to resemble.
 */
@Composable
fun NetworkTab(state: MutableState<Settings>) {
    val scroll = settingsScrollState()
    ControllerAutoScroll(scroll)

    Column(Modifier.fillMaxWidth()) {
        LocalLinkSection(state)
    }
}
