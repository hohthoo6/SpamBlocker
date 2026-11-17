package spam.blocker.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.Button
import spam.blocker.ui.widgets.GreenDot
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.ResImage
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf

@Composable
fun GloballyEnabledSummaryIcons() {
    val C = G.palette
    ResImage(
        R.drawable.ic_call,
        if (G.callEnabled.value) C.teal200 else C.disabled,
        M.size(20.dp)
    )
}

@Composable
fun GloballyEnabled() {
    val ctx = LocalContext.current
    val C = G.palette
    val spfSections = spf.SettingSections(ctx)
    val spfGlobal = spf.Global(ctx)

    var collapsed by remember { mutableStateOf(spfGlobal.isCollapsed) }
    fun expand() {
        collapsed = false
        spfGlobal.isCollapsed = false
    }
    fun collapse() {
        collapsed = true
        spfGlobal.isCollapsed = true
    }
    fun toggleCollapse() {
        collapsed = !collapsed
        spfGlobal.isCollapsed = collapsed
    }
    Section(
        title = Str(R.string.screening),
        horizontalPadding = 8,
        isCollapsed = remember { mutableStateOf(spfSections.isScreeningSectionCollapsed) },
        onToggle = {
            spfSections.isScreeningSectionCollapsed = it
        },
        contentCollapsed = { GloballyEnabledSummary() }
    ) {
        Column {
            LabeledRow(
                R.string.enable,
                helpTooltip = Str(R.string.help_screening),
                isCollapsed = collapsed,
                toggleCollapse = {
                    toggleCollapse()
                },
                content = {
                    if (G.globallyEnabled.value) {
                        Button(
                            content = {
                                RowVCenterSpaced(4) {
                                    GloballyEnabledSummaryIcons()
                                }
                            }
                        ) {
                            toggleCollapse()
                        }
                    }
                    SwitchBox(G.globallyEnabled.value) { enabled ->
                        spfGlobal.isGloballyEnabled = enabled
                        G.globallyEnabled.value = enabled
                        if (enabled) {
                            if (!G.callEnabled.value) {
                                expand()
                            }
                        } else {
                            collapse()
                        }
                    }
                }
            )
            AnimatedVisibleV (!collapsed) {
                Column(modifier = M.padding(start = 16.dp)) {
                    LabeledRow(
                        labelId = R.string.call,
                    ) {
                        SwitchBox(checked = G.callEnabled.value, onCheckedChange = { isTurningOn ->
                            if (isTurningOn) {
                                G.permissionChain.ask(
                                    ctx,
                                    listOf(
                                        PermissionWrapper(Permission.callScreening),
                                    )
                                ) { granted ->
                                    if (granted) {
                                        spfGlobal.isCallEnabled = true
                                        G.callEnabled.value = true
                                    }
                                }
                            } else {
                                spfGlobal.isCallEnabled = false
                                G.callEnabled.value = false
                            }
                        })
                    }

                }
            }
        }
    }

}

@Composable
fun GloballyEnabledSummary() {
    val ctx = LocalContext.current
    val C = G.palette
    val spf = spf.Global(ctx)
    G.globallyEnabled
    G.callEnabled

    if (G.globallyEnabled.value) {
        RowVCenterSpaced(4) {
            GreenDot()

            GloballyEnabledSummaryIcons()
        }
    } else {
        ResImage(
            R.drawable.ic_tile,
            C.disabled,
            M.size(20.dp)
        )
    }
}