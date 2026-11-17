package spam.blocker.ui.setting

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def.ANDROID_12
import spam.blocker.service.CallScreeningService
import spam.blocker.ui.priorityInlineMap
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.FloatingWindow
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.SimPicker
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.A
import spam.blocker.util.Clipboard
import spam.blocker.util.JetpackTextLogger
import spam.blocker.util.MultiLogger
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.SaveableLogger
import spam.blocker.util.Util
import spam.blocker.util.spf


class TestingViewModel {
    val phone = mutableStateOf("")
    val callerName = mutableStateOf("")
    val simSlot = mutableStateOf<Int?>(null)
    val showCallerID = mutableStateOf(false)
}


@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun TestDialog(
    trigger: MutableState<Boolean>,
) {
    val ctx = LocalContext.current
    val C = G.palette

    val coroutine = rememberCoroutineScope()

    val logStr = remember { mutableStateOf(buildAnnotatedString {}) }

    // Log output dialog
    val logTrigger = rememberSaveable { mutableStateOf(false) }
    PopupDialog(
        trigger = logTrigger,
        popupSize = PopupSize(maxWidthPercentage = 0.9f, minWidthDp = 320, maxWidthDp = 1200),
        buttons = if (logStr.value.text.length > 3000) {
            {
                StrokeButton(Str(R.string.copy), color = C.teal200) {
                    Clipboard.copy(ctx, logStr.value.text)
                }
            }
        } else {
            null
        }
    ) {
        Text(
            text = logStr.value,
            color = C.textGrey, // the default text color
            inlineContent = priorityInlineMap()
        )
    }


    fun clearPreviousResult() {
        logStr.value = buildAnnotatedString { }
    }

    val vm = G.testingVM

    PopupDialog(
        trigger = trigger,
        popupSize = PopupSize(maxWidthPercentage = 0.8f, minWidthDp = 340, maxWidthDp = 500),
        onDismiss = {
            if (spf.CallerID(ctx).isEnabled) {
                FloatingWindow.hide(ctx)
            }
        },
        title = {
            RowVCenter {
                GreyLabel(Str(R.string.title_rule_testing))
                BalloonQuestionMark(Str(R.string.help_test_rules))
            }
        },
        buttons = {
            // Test Button
            StrokeButton(label = Str(R.string.test), color = C.teal200) {
                clearPreviousResult()
                logTrigger.value = true

                val multiLogger = MultiLogger(listOf(
                    JetpackTextLogger(logStr),
                    SaveableLogger()
                ))

                coroutine.launch(IO) {
                    CallScreeningService.processCall(
                        ctx, rawNumber = vm.phone.value, simSlot = vm.simSlot.value,
                        callDetails = null, cnap = vm.callerName.value.ifEmpty { null },
                        isTest = true, logger = multiLogger, showCallerId = vm.showCallerID.value
                    )
                }
            }
        },
        content = {
            Column {
                // SIM
                LabeledRow(
                    labelId = R.string.sim_card,
                    color = if (Build.VERSION.SDK_INT < ANDROID_12) C.disabled else null,
                    helpTooltip = Str(R.string.help_test_sim_card)
                ) {
                    SimPicker(vm.simSlot)
                }

                // Show Caller ID
                AnimatedVisibleV(spf.CallerID(ctx).isEnabled) {
                    LabeledRow(
                        labelId = R.string.caller_id
                    ) {
                        SwitchBox(vm.showCallerID.value) { isTurningOn ->
                            if (isTurningOn) {
                                G.permissionChain.ask(
                                    ctx,
                                    listOf(
                                        PermissionWrapper(Permission.showOverlay),
                                        // Permission.phoneState is needed to close the floating dialog when the call gets answered or rejected in real case.
                                        // It's also needed here, otherwise the dialog would work fine when testing but fail on real calls.
                                        PermissionWrapper(Permission.phoneState),
                                    )
                                ) { granted ->
                                    if (granted) {
                                        vm.showCallerID.value = true
                                    }
                                }
                            } else {
                                vm.showCallerID.value = false
                            }
                        }
                    }
                }

                val geoLocation = remember(vm.phone.value) {
                    Util.numberGeoLocation(ctx, vm.phone.value)
                }
                val carrier = remember(vm.phone.value) {
                    Util.numberCarrier(ctx, vm.phone.value)
                }

                // Phone number
                StrInputBox(
                    text = vm.phone.value,
                    label = { GreyLabel(Str(R.string.phone_number)) },
                    leadingIconId = R.drawable.ic_call,
                    onValueChange = {
                        vm.phone.value = it
                        clearPreviousResult()
                    },
                    supportingText = listOfNotNull(
                        // Geolocation
                        geoLocation?.let {
                            Str(R.string.label_value_pair).format(
                                Str(R.string.geolocation), geoLocation
                            )
                        },
                        // Carrier
                        carrier?.let {
                            Str(R.string.label_value_pair).format(
                                Str(R.string.carrier), carrier
                            )
                        }
                    ).takeIf { it.isNotEmpty() }?.joinToString("\n")?.A(C.textGrey),
                )

                // Caller Name
                StrInputBox(
                    text = vm.callerName.value,
                    label = { GreyLabel(Str(R.string.caller_name)) },
                    leadingIconId = R.drawable.ic_id_card,
                    onValueChange = {
                        vm.callerName.value = it
                        clearPreviousResult()
                    },
                )

                if (!G.callEnabled.value) {
                    Text(
                        text = Str(R.string.call_screening_not_enabled),
                        color = C.warning,
                    )
                }
            }
        }
    )
}
