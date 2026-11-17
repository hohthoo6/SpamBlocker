package spam.blocker.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import spam.blocker.R
import spam.blocker.db.Notification.CHANNEL_HIGH
import spam.blocker.db.Notification.CHANNEL_HIGH_MUTED
import spam.blocker.db.Notification.CHANNEL_LOW
import spam.blocker.def.Def
import spam.blocker.def.Def.DEFAULT_HANG_UP_DELAY
import spam.blocker.ui.setting.quick.DefaultCallerIdBgColor
import spam.blocker.ui.setting.quick.DefaultCallerIdTemplate
import spam.blocker.ui.theme.Black1E1E1E
import spam.blocker.ui.theme.ColdGrey
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.Emerald
import spam.blocker.ui.theme.LightMagenta
import spam.blocker.ui.theme.MayaBlue
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SilverGrey
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.util.TimeUtils.FreshnessColor
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class spf { // for namespace only

    open class SharedPref(ctx: Context) {
        val prefs: SharedPreferences = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        companion object {
            private const val PREFS_NAME = "settings"
        }

        fun str(
            key: String,
            defaultValue: String = ""
        ) : ReadWriteProperty<Any?, String> = object : ReadWriteProperty<Any?, String> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): String {
                return prefs.getString(key, defaultValue) ?: defaultValue
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
                prefs.edit { putString(key, value) }
            }
        }

        fun int(
            key: String,
            defaultValue: Int = 0
        ) : ReadWriteProperty<Any?, Int> = object : ReadWriteProperty<Any?, Int> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
                return prefs.getInt(key, defaultValue)
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                prefs.edit { putInt(key, value) }
            }
        }

        fun long(
            key: String,
            defaultValue: Long = 0
        ) : ReadWriteProperty<Any?, Long> = object : ReadWriteProperty<Any?, Long> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Long {
                return prefs.getLong(key, defaultValue)
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
                prefs.edit { putLong(key, value) }
            }
        }

        fun float(
            key: String,
            defaultValue: Float = 0.0f
        ) : ReadWriteProperty<Any?, Float> = object : ReadWriteProperty<Any?, Float> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Float {
                return prefs.getFloat(key, defaultValue)
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
                prefs.edit { putFloat(key, value) }
            }
        }

        fun bool(
            key: String,
            defaultValue: Boolean = false
        ) : ReadWriteProperty<Any?, Boolean> = object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
                return prefs.getBoolean(key, defaultValue)
            }
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
                prefs.edit { putBoolean(key, value) }
            }
        }

        // Write nothing, wait for all async operations to complete.
        // Use this before restarting the app process.
        fun flush() {
            // Simulate flush by calling commit() on a new editor
            prefs.edit(commit = true) {
            }
        }

        fun clear() {
            prefs.edit() {
                clear()
            }
        }
    }

    // This class is instantiated once and cached in GlobalVariables.kt, it holds all colors used by the app.
    class Palette(ctx: Context) : SharedPref(ctx) {
        var allColors = listOf<Delegate>()

        inner class Delegate(
            val key: String,
            val default: Color,
            val labelId: Int,
        )  {
            val state: MutableState<Color> = mutableStateOf(
                Color(prefs.getInt("color_$key", default.toArgb()))
            )

            // These two functions for the `by` keyword
            operator fun getValue(thisRef: Any?, property: KProperty<*>): Color {
                return state.value
            }
            operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Color) {
                update(value)
            }

            fun update(newColor: Color) {
                state.value = newColor
                prefs.edit { putInt("color_$key", newColor.toArgb()) }
            }

            fun reset() = update(default)
        }

        fun color(
            key: String,
            default: Color,
            labelId: Int
        ) : Delegate {
            val d = Delegate(key, default, labelId)
            allColors += d // add each Delegate to the list 
            return d
        }

        var textGrey by color("text", SilverGrey, R.string.normal_text)
        var infoBlue by color("info", SkyBlue, R.string.info_feature)
        var warning by color("warning", DarkOrange, R.string.warning)
        var success by color("success", Emerald, R.string.success_allow)
        var error by color("error", Salmon, R.string.error_block)

        var priority by color("priority", LightMagenta, R.string.priority)
        var regexFlags by color("regexFlags", Color.Magenta, R.string.regex_flags)

        var teal200 by color("enabled", Teal200, R.string.enable)
        var disabled by color("disabled", ColdGrey, R.string.disable)

        var background by color("background", Black1E1E1E, R.string.background)
    }

    class Temporary(ctx: Context) : SharedPref(ctx) {
        // For answer + hang up
        var lastCallToBlock by str("last_number_to_block")
        var lastCallTime by long("last_called_time")
        var hangUpDelay by int("last_number_to_block_delay", DEFAULT_HANG_UP_DELAY)

        var ringtone by str("ringtone")

        var encryptBackup by bool("encrypt_backup")
        var backupKey by str("backup_key")
    }

    class Global(ctx: Context) : SharedPref(ctx) {
        var isGloballyEnabled by bool("globally_enable")
        var isCollapsed by bool("global_enable_collapsed", true)
        var isCallEnabled by bool("call_enable")

        var language by str("language")

        var isTestIconClicked by bool("testing_icon_clicked")

        // ---- Settings below will not be backed up ----
        var activeTab by str("active_tab", "setting")

        var hasPromptedForRunningInWorkProfile by bool("warn_running_in_work_profile_once")
        var ignorePriorityConflict by bool("ignore_priority_conflict")
    }

    class SettingSections(ctx: Context) : SharedPref(ctx) {
        var isScreeningSectionCollapsed by bool("screening_section_collapsed")
        var isQuickSettingsCollapsed by bool("quick_settings_collapsed")
        var isRegexSettingsCollapsed by bool("regex_settings_collapsed")
        var isMiscCollapsed by bool("miscellaneous_collapsed")
    }

    class EmergencySituation(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("emergency_enabled")
        var isCollapsed by bool("emergency_collapsed")
        var priority by int("emergency_priority", Int.MAX_VALUE)

        var duration by int("emergency_duration", 120/*Min*/)

        var extraNumbers by str("emergency_extra_numbers")
        fun getExtraNumbers() : List<String> {
            return extraNumbers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        fun setExtraNumbers(numbers: List<String>) {
            extraNumbers = numbers.joinToString(",")
        }

        var timestamp by long("emergency_last_timestamp")
    }

    class HistoryOptions(ctx: Context) : SharedPref(ctx) {
        var showPassed by bool("show_passed", true)
        var showBlocked by bool("show_blocked", true)
        var showIndicator by bool("show_indicator")
        var showGeoLocation by bool("show_geo_location", true)
        var showCarrier by bool("show_carrier", false)
        var forceShowSim by bool("force_show_sim")

        var isLoggingEnabled by bool("history_logging_enabled", true)
        var isExpiryEnabled by bool("history_expiry_enabled")
        var ttl by int("history_ttl_days", 14) // 14 days

        var searchOnlineUrl by str("search_online_url", "https://google.com/search?q={raw_number}")

        var showTimeColor by bool("show_time_color", true)
        var timeColors by str(
            "time_colors",
            Json.encodeToString(listOf(
                FreshnessColor("10min", Color.Green.toArgb()),
                FreshnessColor("today", MayaBlue.toArgb()),
            ))
        )
        fun loadTimeColors() : List<FreshnessColor> {
            return try {
                Json.decodeFromString<List<FreshnessColor>>(timeColors)
            } catch (_: Exception) {
                listOf()
            }
        }
        fun saveTimeColors(list: List<FreshnessColor>) {
            val sorted = list.sorted()
            timeColors = Json.encodeToString(sorted)
        }
    }

    class Contact(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("contacts_permitted")
        var isStrict by bool("contacts_exclusive")
        var lenientPriority by int("contacts_lenient_priority", 10)
        var strictPriority by int("contacts_strict_priority")
    }

    class Notification(ctx: Context) : SharedPref(ctx) {
        var spamCallChannelId by str("channel_spam_call", CHANNEL_LOW)
    }

    class BlockType(ctx: Context) : SharedPref(ctx) {
        var type by int("block_type", Def.DEF_BLOCK_TYPE)
        var delay by str("block_type_config")
    }

    class RegexOptions(ctx: Context) : SharedPref(ctx) {
        var isNumberCollapsed by bool("number_rule_collapsed")

        var maxNoneScrollRows by int("rule_list_max_none_scroll_rows", 10)
        var maxRegexRows by int("rule_max_regex_rows", 3)
        var maxDescRows by int("rule_max_description_rows", 2)
        var ruleListHeightPercentage by int("rule_list_height_percentage", 60)
        var textboxLimit by int("rule_textbox_max_char_limit", 10000)
    }

    class CallerID(ctx: Context) : SharedPref(ctx) {
        var isEnabled by bool("caller_id")
        var x by int("caller_id_window_x")
        var y by int("caller_id_window_y")
        var bgColor by int("caller_id_bg_color", DefaultCallerIdBgColor)
        var template by str("caller_id_template", DefaultCallerIdTemplate)
    }
}
