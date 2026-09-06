package com.armsx2.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.armsx2.R
import com.armsx2.i18n.str
import com.armsx2.ui.common.padFocusRing
import com.armsx2.ui.premium.Arc
import com.armsx2.ui.premium.ArcIcon
import com.armsx2.ui.premium.AuroraBackground
import com.armsx2.ui.premium.Branding
import com.armsx2.ui.premium.MaterialLevel
import com.armsx2.ui.premium.Palette
import com.armsx2.ui.premium.Radii
import com.armsx2.ui.premium.Type
import com.armsx2.ui.premium.material

/**
 * First-run setup.
 *
 * Shaped like an installer rather than a slideshow: a progress rail down the left listing every
 * step at once, and the current step's work on the right. The previous version set each step's
 * title TWICE at the same time — once as a display-size headline filling the left half, again
 * above the controls — so most of a landscape screen restated what the other half already said
 * while the actual choices were squeezed into a narrow column. Here the left column earns its
 * space by answering "how much of this is left", which is the one question a setup flow has to
 * keep answering.
 *
 * The pages, the pickers and every ViewModel call are unchanged; this is the frame around them.
 */

private data class SetupStep(val titleKey: String, val icon: Int)

private val SETUP_STEPS = listOf(
    SetupStep("setup.page.welcome.title", Arc.info),
    SetupStep("setup.step.appData.title", Arc.saves),
    SetupStep("setup.page.bios.title", Arc.bios),
    SetupStep("setup.page.roms.title", Arc.library),
    SetupStep("setup.button.applyFinish", Arc.check),
)

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel()) {
    val state = viewModel.state.value
    val canContinue = viewModel.canContinue()
    var swipeDistance by remember { mutableFloatStateOf(0f) }
    // BIOS onboarding is folder-based (refresh parity) — pick a folder and every valid BIOS
    // inside is imported and made available here and in the BIOS settings tab.
    val biosPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importBiosFolder)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addGameFolder)
    }
    // github flavor only: a third "Custom folder" data-root, with all-files access
    // (MANAGE_EXTERNAL_STORAGE). The Play build stays SAF-scoped (Internal / SD only).
    // Flow: grant all-files access if needed -> pick a folder -> resolve the tree URI to a
    // POSIX path the native core can write to directly.
    val context = androidx.compose.ui.platform.LocalContext.current
    val customFolderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { u ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    u,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            com.armsx2.runtime.MainActivityRuntime.resolveTreeUriToPosix(u.toString())
                ?.let(viewModel::selectCustomStorage)
        }
    }
    val allFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            android.os.Environment.isExternalStorageManager()
        ) {
            customFolderPicker.launch(null)
        }
    }
    val onCustomStorage: () -> Unit = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !android.os.Environment.isExternalStorageManager()
        ) {
            val manageIntent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}"),
            )
            runCatching { allFilesLauncher.launch(manageIntent) }.onFailure {
                runCatching {
                    allFilesLauncher.launch(
                        android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                    )
                }
            }
        } else {
            customFolderPicker.launch(null)
        }
    }

    LaunchedEffect(Unit) { viewModel.load() }

    Box(Modifier.fillMaxSize().background(Palette.ground)) {
        AuroraBackground(Modifier.fillMaxSize())

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // Every system bar, not just the status bar. Android 15 enforces edge-to-edge, so
                // the window is the whole screen -- and this is one of the few screens that keeps
                // the bars ON SCREEN (a wizard is where you least want the navigation hidden). In
                // landscape a three-button bar sits on the SIDE, which is where the Next button
                // is, so padding only the top left the wizard's one required control underneath
                // it. safeDrawing rather than systemBars because the cutout is also in play:
                // layoutInDisplayCutoutMode is `always`. The aurora is a sibling and still fills
                // the screen, so nothing is boxed in visually.
                .safeDrawingPadding()
                .pointerInput(state.page, state.busy, canContinue) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipeDistance = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            swipeDistance += amount
                        },
                        onDragEnd = {
                            val threshold = size.width * 0.16f
                            when {
                                swipeDistance > threshold && state.page > 0 && !state.busy ->
                                    viewModel.previous()
                                swipeDistance < -threshold && state.page < SETUP_STEPS.lastIndex &&
                                    canContinue && !state.busy -> viewModel.next()
                            }
                            swipeDistance = 0f
                        },
                        onDragCancel = { swipeDistance = 0f },
                    )
                },
        ) {
            // The rail needs room to be worth having. Below that the steps collapse to a slim
            // progress bar above the content rather than squeezing two columns into one.
            val wide = maxWidth >= 720.dp

            Row(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (wide) {
                    StepRail(page = state.page, modifier = Modifier.width(268.dp).fillMaxHeight())
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!wide) {
                        CompactProgress(state.page)
                        Spacer(Modifier.height(16.dp))
                    }
                    AnimatedContent(
                        targetState = state.page,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            val direction = if (targetState > initialState) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            (slideIntoContainer(direction, tween(320)) + fadeIn(tween(220))) togetherWith
                                (slideOutOfContainer(direction, tween(280)) + fadeOut(tween(180)))
                        },
                        label = "setup-page",
                    ) { page ->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            WizardPage(
                                page = page,
                                state = state,
                                viewModel = viewModel,
                                biosPicker = { biosPicker.launch(null) },
                                folderPicker = { folderPicker.launch(null) },
                                onCustomStorage = onCustomStorage,
                            )
                        }
                    }
                    Footer(
                        page = state.page,
                        canContinue = canContinue,
                        busy = state.busy,
                        onBack = viewModel::previous,
                        onNext = if (state.page == SETUP_STEPS.lastIndex) viewModel::finish else viewModel::next,
                    )
                }
            }
        }
    }

    state.error?.let { message ->
        com.armsx2.ui.common.NotifyOverlay(
            title = str("setup.welcome.heading"),
            message = message,
            onDismiss = viewModel::dismissError,
            idPrefix = "setup.error",
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Frame
// ---------------------------------------------------------------------------------------------

/** The left column: the wordmark, then every step at once with the current one lit. */
@Composable
private fun StepRail(page: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.card))
            .padding(horizontal = 22.dp, vertical = 26.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.namco_2x6),
            contentDescription = Branding.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(26.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(str("setup.welcome.subheading"), style = Type.footnote, color = Palette.labelTertiary)

        Spacer(Modifier.height(30.dp))

        SETUP_STEPS.forEachIndexed { index, step ->
            RailStep(
                index = index,
                label = str(step.titleKey),
                state = when {
                    index < page -> RailState.Done
                    index == page -> RailState.Current
                    else -> RailState.Pending
                },
                last = index == SETUP_STEPS.lastIndex,
            )
        }
    }
}

private enum class RailState { Done, Current, Pending }

@Composable
private fun RailStep(index: Int, label: String, state: RailState, last: Boolean) {
    val marker by animateColorAsState(
        when (state) {
            RailState.Current -> Palette.accent
            RailState.Done -> Palette.accent.copy(alpha = 0.30f)
            RailState.Pending -> Color.Transparent
        },
        spring(),
        label = "rail-marker",
    )
    Row {
        // Marker column: the dot plus the rule tying it to the next one, so the steps read as
        // one track rather than five loose rows.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(marker)
                    .then(
                        if (state == RailState.Pending) {
                            Modifier.border(1.dp, Palette.hairline, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (state == RailState.Done) {
                    ArcIcon(Arc.check, tint = Color.White, size = 14.dp)
                } else {
                    Text(
                        "${index + 1}",
                        style = Type.caption,
                        color = if (state == RailState.Current) Color.White else Palette.labelTertiary,
                    )
                }
            }
            if (!last) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .background(
                            if (state == RailState.Done) {
                                Palette.accent.copy(alpha = 0.30f)
                            } else {
                                Palette.hairline
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = if (state == RailState.Current) Type.headline else Type.subheadline,
            color = when (state) {
                RailState.Current -> Palette.label
                RailState.Done -> Palette.labelSecondary
                RailState.Pending -> Palette.labelTertiary
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** Narrow-screen stand-in for the rail: one bar per step, filled up to where you are. */
@Composable
private fun CompactProgress(page: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SETUP_STEPS.indices.forEach { index ->
            val color by animateColorAsState(
                if (index <= page) Palette.accent else Palette.materialThin,
                spring(),
                label = "compact-progress",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(color),
            )
        }
    }
}

/** Eyebrow, title and description — the same header on every step. */
@Composable
private fun StepHeader(page: Int, title: String, description: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${page + 1} / ${SETUP_STEPS.size}",
            style = Type.eyebrow,
            color = Palette.accentBright,
        )
        Spacer(Modifier.height(10.dp))
        Text(title, style = Type.largeTitle, color = Palette.label)
        Spacer(Modifier.height(8.dp))
        Text(description, style = Type.callout, color = Palette.labelSecondary)
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun Footer(
    page: Int,
    canContinue: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page > 0) {
            GhostPill(str("action.back"), enabled = !busy, onClick = onBack)
        }
        Spacer(Modifier.weight(1f))
        PrimaryPill(
            label = if (page == SETUP_STEPS.lastIndex) str("setup.button.letsGo") else str("setup.button.next"),
            enabled = canContinue && !busy,
            busy = busy,
            onClick = onNext,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------------------------

@Composable
private fun PrimaryPill(label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(if (enabled) Palette.accent else Palette.materialThin)
            .clickable(enabled = enabled, onClick = onClick)
            .padFocusRing(RoundedCornerShape(Radii.pill))
            .padding(horizontal = 30.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = Color.White)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = Type.headline, color = if (enabled) Color.White else Palette.labelTertiary)
    }
}

@Composable
private fun GhostPill(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radii.pill))
            .material(MaterialLevel.Thin, RoundedCornerShape(Radii.pill))
            .clickable(enabled = enabled, onClick = onClick)
            .padFocusRing(RoundedCornerShape(Radii.pill))
            .padding(horizontal = 26.dp, vertical = 15.dp),
    ) {
        Text(label, style = Type.headline, color = Palette.labelSecondary)
    }
}

/**
 * A pickable option. Selection shows as an accent border and a check, not as a flooded
 * container: these cards sit on translucent material over the aurora, and a solid fill on the
 * chosen one would punch a hole in that.
 */
@Composable
private fun OptionCard(
    title: String,
    detail: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border by animateColorAsState(
        if (selected) Palette.accentBright else Palette.hairline,
        spring(),
        label = "option-border",
    )
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 74.dp)
            .clip(RoundedCornerShape(Radii.tile))
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
            .border(if (selected) 1.5.dp else 1.dp, border, RoundedCornerShape(Radii.tile))
            .clickable(onClick = onClick)
            .padFocusRing(RoundedCornerShape(Radii.tile))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArcIcon(icon, tint = if (selected) Palette.accentBright else Palette.labelSecondary, size = 21.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.headline, color = Palette.label)
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = Type.footnote,
                color = Palette.labelSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(12.dp))
            CheckDot()
        }
    }
}

@Composable
private fun CheckDot() {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(Palette.accent),
        contentAlignment = Alignment.Center,
    ) { ArcIcon(Arc.check, tint = Color.White, size = 13.dp) }
}

// ---------------------------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------------------------

@Composable
private fun WizardPage(
    page: Int,
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    biosPicker: () -> Unit,
    folderPicker: () -> Unit,
    onCustomStorage: () -> Unit,
) {
    when (page) {
        0 -> WelcomePage()
        1 -> StoragePage(state, viewModel::selectStorage, onCustomStorage)
        2 -> BiosPage(state, onPick = biosPicker, onSelectBios = viewModel::selectBiosCandidate)
        3 -> GamesPage(state, folderPicker, viewModel::removeGameFolder)
        else -> ReadyPage(state)
    }
}

@Composable
private fun WelcomePage() {
    StepHeader(0, str("setup.welcome.heading"), str("setup.systemDir.intro"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WelcomeLine(Arc.saves, str("setup.step.appData.title"), str("setup.step.appData.description.play"))
        WelcomeLine(Arc.bios, str("setup.step.bios.title"), str("setup.step.bios.description"))
        WelcomeLine(Arc.library, str("setup.step.rom.title"), str("setup.step.rom.description"))
    }
}

@Composable
private fun WelcomeLine(icon: Int, title: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArcIcon(icon, tint = Palette.accentBright, size = 20.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.headline, color = Palette.label)
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = Type.footnote,
                color = Palette.labelSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StoragePage(
    state: OnboardingUiState,
    onSelect: (StorageLocation) -> Unit,
    onCustom: () -> Unit,
) {
    StepHeader(1, str("setup.step.appData.title"), str("setup.step.appData.description.play"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionCard(
            title = str("setup.storageChooser.internalShort"),
            detail = str("setup.systemDir.appPrivateSubtitle"),
            icon = Arc.saves,
            selected = state.systemLocation == StorageLocation.Internal,
            onClick = { onSelect(StorageLocation.Internal) },
        )
        OptionCard(
            title = str("setup.systemDir.sdCard"),
            detail = str("setup.step.appData.description.play"),
            icon = Arc.memcard,
            selected = state.systemLocation == StorageLocation.SdCard,
            onClick = { onSelect(StorageLocation.SdCard) },
        )
        // github APK only: custom folder with all-files access.
        if (com.armsx2.BuildConfig.STORAGE_ALL_FILES) {
            OptionCard(
                title = str("setup.storageChooser.customShort"),
                detail = str("setup.storageChooser.customSubtitle"),
                icon = Arc.folder,
                selected = state.systemLocation == StorageLocation.Custom,
                onClick = onCustom,
            )
        }
    }
}

@Composable
private fun BiosPage(state: OnboardingUiState, onPick: () -> Unit, onSelectBios: (BiosCandidate) -> Unit) {
    StepHeader(2, str("setup.page.bios.title"), str("setup.step.bios.description"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            state.biosInfo == null -> OptionCard(
                title = str("setup.bios.selectTitle"),
                detail = str("setup.button.choose"),
                icon = Arc.folder,
                selected = false,
                onClick = onPick,
            )
            // A folder import turned up several BIOSes — let the user pick the active one here
            // instead of accepting the auto-selected first entry.
            state.biosOptions.size > 1 -> {
                Text(
                    "${state.biosOptions.size} ${str("setup.bios.multipleFound")}",
                    style = Type.footnote,
                    color = Palette.labelSecondary,
                )
                state.biosOptions.forEach { candidate ->
                    BiosRow(
                        candidate = candidate,
                        selected = candidate.path == state.selectedBiosPath,
                        onClick = { onSelectBios(candidate) },
                    )
                }
                GhostPill(str("setup.button.pickDifferentFolder"), enabled = true, onClick = onPick)
            }
            else -> {
                SelectedBios(state)
                GhostPill(str("setup.button.pickDifferentFolder"), enabled = true, onClick = onPick)
            }
        }
        if (state.busy) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Palette.accentBright)
                Spacer(Modifier.width(12.dp))
                Text(str("setup.bios.scanning"), style = Type.footnote, color = Palette.labelSecondary)
            }
        }
    }
}

@Composable
private fun BiosRow(candidate: BiosCandidate, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.tile))
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) Palette.accentBright else Palette.hairline,
                RoundedCornerShape(Radii.tile),
            )
            .clickable(onClick = onClick)
            .padFocusRing(RoundedCornerShape(Radii.tile))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RegionBadge(candidate.info.regionFlag)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                candidate.name,
                style = Type.subheadline,
                color = Palette.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(candidate.info.description, candidate.info.versionString).joinToString(" · "),
                style = Type.footnote,
                color = Palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(12.dp))
            CheckDot()
        }
    }
}

@Composable
private fun SelectedBios(state: OnboardingUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.tile))
            .material(MaterialLevel.Thin, RoundedCornerShape(Radii.tile))
            .border(1.5.dp, Palette.accentBright, RoundedCornerShape(Radii.tile))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RegionBadge(state.biosInfo?.regionFlag.orEmpty())
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                state.biosName ?: str("setup.bios.selectTitle"),
                style = Type.headline,
                color = Palette.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(state.biosInfo?.description, state.biosInfo?.versionString).joinToString(" · "),
                style = Type.footnote,
                color = Palette.labelSecondary,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(12.dp))
        CheckDot()
    }
}

/** The BIOS region, set as the short code BiosInfo now returns rather than a flag emoji. */
@Composable
private fun RegionBadge(code: String) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 46.dp)
            .clip(RoundedCornerShape(Radii.chip))
            .background(Palette.materialThin)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(code, style = Type.caption, color = Palette.label, maxLines = 1)
    }
}

@Composable
private fun GamesPage(state: OnboardingUiState, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    StepHeader(3, str("setup.page.roms.title"), str("setup.step.rom.description"))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.gameFolders.forEach { raw ->
            val label = Uri.parse(raw).lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: raw
            Row(
                Modifier
                    .fillMaxWidth()
                    .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
                    .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArcIcon(Arc.folder, tint = Palette.accentBright, size = 19.dp)
                Spacer(Modifier.width(16.dp))
                Text(
                    label,
                    style = Type.subheadline,
                    color = Palette.label,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    str("setup.button.remove"),
                    style = Type.footnote,
                    color = Palette.accentBright,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .clickable { onRemove(raw) }
                        .padFocusRing(RoundedCornerShape(Radii.pill))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        OptionCard(
            title = if (state.gameFolders.isEmpty()) {
                str("setup.button.pickRomsFolder")
            } else {
                str("setup.button.addAnotherFolder")
            },
            detail = str("setup.step.rom.description"),
            icon = Arc.plus,
            selected = false,
            onClick = onAdd,
        )
    }
}

@Composable
private fun ReadyPage(state: OnboardingUiState) {
    StepHeader(4, str("setup.button.applyFinish"), str("games.scanningRoms"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryTile(
            str("setup.step.appData.title"),
            when (state.systemLocation) {
                StorageLocation.Internal -> str("setup.storageChooser.internalShort")
                StorageLocation.SdCard -> str("setup.systemDir.sdCard")
                StorageLocation.Custom -> str("setup.storageChooser.customShort")
            },
            Arc.saves,
            Modifier.weight(1f),
        )
        SummaryTile(
            str("setup.step.bios.title"),
            state.biosInfo?.versionString ?: str("setup.status.notSelected"),
            Arc.bios,
            Modifier.weight(1f),
        )
        SummaryTile(
            str("setup.step.rom.title"),
            state.gameFolders.size.toString(),
            Arc.library,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryTile(title: String, value: String, icon: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .material(MaterialLevel.UltraThin, RoundedCornerShape(Radii.tile))
            .padding(18.dp),
    ) {
        ArcIcon(icon, tint = Palette.accentBright, size = 19.dp)
        Spacer(Modifier.height(14.dp))
        Text(title, style = Type.footnote, color = Palette.labelSecondary)
        Spacer(Modifier.height(3.dp))
        Text(value, style = Type.title3, color = Palette.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
