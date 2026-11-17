package spam.blocker.ui.setting

import android.view.ViewTreeObserver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.service.checker.IChecker
import spam.blocker.ui.M
import spam.blocker.ui.setting.misc.About
import spam.blocker.ui.setting.misc.BackupRestore
import spam.blocker.ui.setting.misc.FAQ
import spam.blocker.ui.setting.misc.Language
import spam.blocker.ui.setting.misc.Theme
import spam.blocker.ui.setting.misc.VersionSummary
import spam.blocker.ui.setting.quick.BlockType
import spam.blocker.ui.setting.quick.BlockTypeSummary
import spam.blocker.ui.setting.quick.CallerID
import spam.blocker.ui.setting.quick.CallerIDSummary
import spam.blocker.ui.setting.quick.Contacts
import spam.blocker.ui.setting.quick.ContactsSummary
import spam.blocker.ui.setting.quick.EmergencySituation
import spam.blocker.ui.setting.quick.EmergencySituationSummary
import spam.blocker.ui.setting.quick.Notification
import spam.blocker.ui.setting.quick.NotificationSummary
import spam.blocker.ui.slightDiff
import spam.blocker.ui.theme.White
import spam.blocker.ui.setting.regex.RegexHeader
import spam.blocker.ui.setting.regex.RegexList
import spam.blocker.ui.setting.regex.RegexSummary
import spam.blocker.ui.setting.regex.RegexViewModel
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.Fab
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.NormalColumnScrollbar
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SearchBox
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.util.Lambda
import spam.blocker.util.Util.isFreshInstall
import spam.blocker.util.spf

const val SettingRowMinHeight = 40

@Composable
fun SettingScreen() {
    val C = G.palette
    val ctx = LocalContext.current

    val testingTrigger = rememberSaveable { mutableStateOf(false) }
    TestDialog(testingTrigger)

    // Hide FAB when scrolled to the bottom
    val scrollState = rememberScrollState()
    val bottomReached by remember {
        derivedStateOf {
            scrollState.maxValue > 0 && scrollState.value == scrollState.maxValue
        }
    }

    val priorityConflicts = remember { mutableStateListOf<IChecker>() }

    fun checkConflicts() {
        priorityConflicts.apply {
            clear()
            if (!spf.Global(ctx).ignorePriorityConflict) {
                addAll(detectConflictCheckers(ctx))
            }
        }
    }
    // Detect priority conflicts when recomposed (e.g. on tab switching)
    LaunchedEffect(Unit) {
        checkConflicts()
    }

    // Detect priority conflicts when this screen regains focus (e.g., after closing a popup)
    val view = LocalView.current
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                checkConflicts()
            }
        }

        val observer = view.viewTreeObserver
        observer.addOnWindowFocusChangeListener(listener)

        onDispose {
            if (observer.isAlive) {
                observer.removeOnWindowFocusChangeListener(listener)
            }
        }
    }

    // Detect conflicts when clicking the "Test" button, show a popup warning if there are conflicts.
    val conflictTrigger = remember { mutableStateOf(false) }
    PriorityConflictDialog(trigger = conflictTrigger, conflicts = priorityConflicts)

    // Show text "Testing" on the testing tube icon, and hide this text once it's clicked.
    val spfSections = spf.SettingSections(ctx)
    val spfGlobal = spf.Global(ctx)

    var alsoShowTestButtonLabel by remember {
        mutableStateOf(
            isFreshInstall(ctx) && !spfGlobal.isTestIconClicked
        )
    }
    FabWrapper(
        fabRow = { positionModifier ->
            Fab(
                visible = !bottomReached,
                text = if (alsoShowTestButtonLabel) Str(R.string.title_rule_testing) else null,
                iconId = R.drawable.ic_tube,
                iconColor = White,
                bgColor = if (priorityConflicts.isEmpty()) C.teal200 else C.warning,
                modifier = positionModifier
            ) {
                checkConflicts()
                if (priorityConflicts.isEmpty()) {
                    testingTrigger.value = true

                    spfGlobal.isTestIconClicked = true
                    alsoShowTestButtonLabel = false
                } else {
                    conflictTrigger.value = true
                }
            }
        }
    ) {

        NormalColumnScrollbar(state = scrollState) {
            Column(
                modifier = M
                    .verticalScroll(scrollState)
                    .padding(top = 8.dp)
            ) {
                // global
                GloballyEnabled()

                // quick settings
                Section(
                    title = Str(R.string.quick_settings),
                    horizontalPadding = 8,
                    isCollapsed = remember { mutableStateOf(spfSections.isQuickSettingsCollapsed) },
                    onToggle = {
                        spfSections.isQuickSettingsCollapsed = it
                    },
                    content = {
                        Column {
                            Contacts()
                            EmergencySituation()

                            HorizontalDivider(thickness = 1.dp, color = G.palette.background.slightDiff())

                            BlockType()
                            Notification()
                            CallerID()
                        }
                    },
                    contentCollapsed = {
                        FlowRowSpaced(4) {
                            ContactsSummary()
                            EmergencySituationSummary()

                            HorizontalDivider(modifier = M.padding(vertical = 3.dp), thickness = 1.dp, color = G.palette.background.slightDiff())

                            BlockTypeSummary()
                            NotificationSummary()
                            CallerIDSummary()
                        }
                    }
                )

                // Regex / number rules
                Section(
                    title = Str(R.string.regex_settings),
                    horizontalPadding = 8,
                    isCollapsed = remember { mutableStateOf(spfSections.isRegexSettingsCollapsed) },
                    onToggle = {
                        spfSections.isRegexSettingsCollapsed = it
                    },
                    content = {
                        Column {
                            val vm: RegexViewModel = G.NumberRuleVM
                            vm.reloadOptions(ctx)
                            LaunchedEffect(true) { vm.reloadDbAndOptions(ctx) }

                            RegexHeader(vm)
                            AnimatedVisibleV(!vm.listCollapsed.value) {
                                Column {
                                    SearchBox(vm.searchEnabled, vm.filter) {
                                        vm.reloadDb(ctx)
                                    }
                                    RegexList(vm)
                                }
                            }
                        }
                    },
                    contentCollapsed = {
                        RegexSummary(G.NumberRuleVM)
                    }
                )

                // Miscellaneous
                Section(
                    title = Str(R.string.miscellaneous),
                    horizontalPadding = 8,
                    isCollapsed = remember { mutableStateOf(spfSections.isMiscCollapsed) },
                    onToggle = {
                        spfSections.isMiscCollapsed = it
                    },
                    content = {
                        Column {
                            Language()
                            Theme()
                            BackupRestore()
                            SettingRow {
                                RowVCenterSpaced(8) {
                                    FAQ()
                                    About()
                                }
                            }
                        }
                    },
                    contentCollapsed = {
                        VersionSummary()
                    }
                )
            }
        }
    }
}


@Composable
fun SettingRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    RowVCenter(
        modifier = modifier
            .heightIn(min = SettingRowMinHeight.dp)
            .padding(vertical = 2.dp)
    ) {
        content()
    }
}

@Composable
fun SettingLabel(
    labelId: Int,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Text(
        text = stringResource(id = labelId),
        modifier = modifier,
        maxLines = 1,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = color ?: G.palette.infoBlue,
    )
}

// This is used in SettingScreen
@Composable
fun LabeledRow(
    labelId: Int?,
    modifier: Modifier = Modifier,
    color: Color? = null,

    // Padding indentation for labels in Section Group
    paddingHorizontal: Int = 0,

    // Show the question icon or not
    helpTooltip: String? = null,

    // Show a down arrow to indicate the content below is collapsed
    // - null: it's not collapsable
    // - true/false: if it's collapsed or not
    isCollapsed: Boolean? = false,
    toggleCollapse: Lambda? = null,

    // Items on the right side, e.g.: "New" button
    content: @Composable RowScope.() -> Unit,
) {

    SettingRow(
        modifier = modifier
            .clickable {
                // 1. expand/collapse
                if (toggleCollapse != null)
                    toggleCollapse()
            }
            .padding(horizontal = paddingHorizontal.dp),
    ) {
        RowVCenterSpaced(
            space = 2,
            modifier = M.wrapContentWidth()
        ) {
            // label
            if (labelId != null) {
                SettingLabel(
                    labelId,
                    color = color
                )
            }

            // collapsed indicator
            if (isCollapsed == true) {
                GreyIcon16(
                    iconId = R.drawable.ic_dropdown_arrow,
                )
            }

            // balloon tooltip
            helpTooltip?.let {
                BalloonQuestionMark(it)
            }

            // rest content
            RowVCenter(
                modifier = M.weight(1f),
                horizontalArrangement = Arrangement.End,
            ) {
                content()
            }
        }
    }
}
