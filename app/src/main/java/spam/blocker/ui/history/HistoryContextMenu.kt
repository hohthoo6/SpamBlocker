package spam.blocker.ui.history

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.HistoryRecord
import spam.blocker.ui.M
import spam.blocker.ui.setting.TestDialog
import spam.blocker.ui.widgets.CustomItem
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.util.Clipboard
import spam.blocker.util.Launcher
import spam.blocker.util.spf

@Composable
fun SearchOnlineConfigDialog(
    trigger: MutableState<Boolean>
) {
    PopupDialog(trigger) {
        val ctx = LocalContext.current
        val spf = spf.HistoryOptions(ctx)

        var url by retain { mutableStateOf(spf.searchOnlineUrl) }

        StrInputBox(
            text = url,
            label = { Text(Str(R.string.search_engine)) },
            helpTooltip = Str(R.string.tags_supported) + Str(R.string.raw_number_tag),
            onValueChange = {
                url = it
                spf.searchOnlineUrl = it
            },
            leadingIconId = R.drawable.ic_find,
            maxLines = 10,
        )
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun HistoryContextMenuWrapper(
    vm: HistoryViewModel,
    record: HistoryRecord,
    content: @Composable (MutableState<Boolean>) -> Unit,
) {
    val ctx = LocalContext.current
    val C = G.palette

    var menuExpanded by remember { mutableStateOf(false) }

    // Context menu items
    var menuItems by remember { mutableStateOf(listOf<IMenuItem>()) }

    val triggerSearchOnline = retain { mutableStateOf(false) }
    SearchOnlineConfigDialog(triggerSearchOnline)

    // Test
    val testTrigger = rememberSaveable { mutableStateOf(false) }
    TestDialog(testTrigger)

    // Refresh context menu items when it's being expanded
    LaunchedEffect(menuExpanded) {
        if (menuExpanded) { // don't do anything when not expanded, for better performance
            menuItems = listOf(
                // Test
                LabelItem(
                    label = ctx.getString(R.string.test),
                    leadingIcon = {
                        ResIcon(
                            R.drawable.ic_tube,
                            modifier = M.size(20.dp),
                            color = C.teal200
                        )
                    }
                ) {
                    G.testingVM.apply {
                        phone.value = record.peer
                        callerName.value = record.cnap ?: ""
                        simSlot.value = record.simSlot
                    }
                    testTrigger.value = true
                },

                // Multi select
                LabelItem(
                    label = ctx.getString(R.string.multi_select),
                    leadingIcon = { GreyIcon20(R.drawable.ic_select) }
                ) {
                    vm.multiSelect.value = true
                    vm.selectedIds.clear()
                    vm.selectedIds.add(record.id)
                },

                // Copy
                LabelItem(
                    label = ctx.getString(R.string.copy_number),
                    leadingIcon = { GreyIcon20(R.drawable.ic_copy) }
                ) {
                    Clipboard.copy(ctx, record.peer)
                },

                // Search Online
                CustomItem {
                    RowVCenterSpaced(
                        space = 10,
                        modifier = M.padding(horizontal = 0.dp)
                            .clickable {
                                val url = spf.HistoryOptions(ctx).searchOnlineUrl
                                    .replace("{raw_number}", record.peer)
                                Launcher.openUrl(ctx, url)
                                menuExpanded = false
                            }
                    ) {
                        GreyIcon20(R.drawable.ic_find)
                        GreyLabel(Str(R.string.search_online))
                        GreyIcon20(
                            iconId = R.drawable.ic_settings,
                            modifier = M.clickable {
                                triggerSearchOnline.value = true
                            }
                        )
                    }
                },

                // Filter
                LabelItem(
                    label = ctx.getString(R.string.filter),
                    leadingIcon = { GreyIcon20(R.drawable.ic_filter) }
                ) {
                    vm.searchEnabled.value = true
                }
            )
        }
    }

    DropdownWrapper(
        items = menuItems,
        content = { contextMenuExpanded ->
            // Refresh all menu items on expanding
            LaunchedEffect(contextMenuExpanded.value) {
                menuExpanded = contextMenuExpanded.value
            }
            content(contextMenuExpanded)
        }
    )
}
