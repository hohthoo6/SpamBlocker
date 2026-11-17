package spam.blocker.config

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.HistoryRecord
import spam.blocker.db.Notification.Channel
import spam.blocker.db.Notification.ChannelTable
import spam.blocker.util.MyJson
import spam.blocker.util.Notification.createChannel
import spam.blocker.util.Notification.deleteAllChannels
import spam.blocker.util.Permission
import spam.blocker.util.spf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val ConfigJson = Json(MyJson) {
    explicitNulls = false
}

interface IConfig {
    fun load(ctx: Context) // load current settings into this object before saving to a backup file
    fun apply(ctx: Context) // import from a backup file and override the current settings.
}

@Serializable
class Global : IConfig {
    var enabled = false
    var collapsed = false
    var callEnabled = false

    // misc
    var isTestingIconClicked = false

    override fun load(ctx: Context) {
        val spf = spf.Global(ctx)
        enabled = spf.isGloballyEnabled
        collapsed = spf.isCollapsed
        callEnabled = spf.isCallEnabled

        isTestingIconClicked = spf.isTestIconClicked
    }

    override fun apply(ctx: Context) {
        spf.Global(ctx).apply {
            isGloballyEnabled = enabled
            isCollapsed = collapsed
            isCallEnabled = callEnabled

            isTestIconClicked = isTestingIconClicked
        }
    }
}

@Serializable
class SettingSections : IConfig {
    var isScreeningSectionCollapsed = false
    var isQuickSettingsCollapsed = false
    var isMiscCollapsed = false

    override fun load(ctx: Context) {
        val spf = spf.SettingSections(ctx)

        isScreeningSectionCollapsed = spf.isScreeningSectionCollapsed
        isQuickSettingsCollapsed = spf.isQuickSettingsCollapsed
        isMiscCollapsed = spf.isMiscCollapsed
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.SettingSections(ctx).apply {
            isScreeningSectionCollapsed = me.isScreeningSectionCollapsed
            isQuickSettingsCollapsed = me.isQuickSettingsCollapsed
            isMiscCollapsed = me.isMiscCollapsed
        }
    }
}

@Serializable
class HistoryOptions : IConfig {
    var showPassed = true
    var showBlocked = true
    var showIndicator = false
    var showGeoLocation = false
    var showCarrier = false
    var forceShowSim = false
    var loggingEnabled = true
    var expiryEnabled = true
    var ttl = -1
    var showTimeColor = false
    var timeColors = ""
    var searchOnlineUrl = ""

    override fun load(ctx: Context) {
        val spf = spf.HistoryOptions(ctx)
        showPassed = spf.showPassed
        showBlocked = spf.showBlocked
        showIndicator = spf.showIndicator
        showGeoLocation = spf.showGeoLocation
        showCarrier = spf.showCarrier
        forceShowSim = spf.forceShowSim
        loggingEnabled = spf.isLoggingEnabled
        expiryEnabled = spf.isExpiryEnabled
        ttl = spf.ttl
        showTimeColor = spf.showTimeColor
        timeColors = spf.timeColors
        searchOnlineUrl = spf.searchOnlineUrl
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.HistoryOptions(ctx).apply {
            showPassed = me.showPassed
            showBlocked = me.showBlocked
            showIndicator = me.showIndicator
            showGeoLocation = me.showGeoLocation
            showCarrier = me.showCarrier
            forceShowSim = me.forceShowSim
            isLoggingEnabled = me.loggingEnabled
            isExpiryEnabled = me.expiryEnabled
            ttl = me.ttl
            showTimeColor = me.showTimeColor
            timeColors = me.timeColors
            searchOnlineUrl = me.searchOnlineUrl
        }
    }
}

@Serializable
class EmergencySituation : IConfig {
    var enabled = false
    var priority = Int.MAX_VALUE
    var collapsed = false
    var duration = 0
    var extraNumbers = listOf<String>()
    var timestamp: Long = 0

    override fun load(ctx: Context) {
        val spf = spf.EmergencySituation(ctx)
        enabled = spf.isEnabled
        priority = spf.priority
        collapsed = spf.isCollapsed
        duration = spf.duration
        extraNumbers = spf.getExtraNumbers()
        timestamp = spf.timestamp
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.EmergencySituation(ctx).apply {
            isEnabled = me.enabled
            priority = me.priority
            isCollapsed = me.collapsed
            duration = me.duration
            setExtraNumbers(me.extraNumbers)
            timestamp = me.timestamp
        }
    }
}

@Serializable
class Theme : IConfig {
    var colorMap: MutableMap<String, Int> = mutableMapOf()

    override fun load(ctx: Context) {
        spf.Palette(ctx).allColors.forEach {
            colorMap[it.key] = it.state.value.toArgb()
        }
    }

    override fun apply(ctx: Context) {
        // Iterate through spf.allColors
        spf.Palette(ctx).allColors.forEach { delegate ->
            // Apply new color if it exists in the config
            colorMap[delegate.key]?.let {
                delegate.update(Color(it))
            }
        }
    }
}

@Serializable
class Language : IConfig {
    var lang = ""
    override fun load(ctx: Context) {
        lang = spf.Global(ctx).language
    }

    override fun apply(ctx: Context) {
        spf.Global(ctx).language = lang
    }
}

@Serializable
class Contact : IConfig {
    var enabled = false
    var isExcusive = false
    var permissivePriority = 0
    var strictPriority = 0
    override fun load(ctx: Context) {
        val spf = spf.Contact(ctx)
        enabled = spf.isEnabled
        isExcusive = spf.isStrict
        permissivePriority = spf.lenientPriority
        strictPriority = spf.strictPriority
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.Contact(ctx).apply {
            isEnabled = me.enabled
            isStrict = me.isExcusive
            lenientPriority = me.permissivePriority
            strictPriority = me.strictPriority
        }
    }
}

@Serializable
class BlockType : IConfig {
    var type = 0
    var config = ""
    override fun load(ctx: Context) {
        val spf = spf.BlockType(ctx)
        type = spf.type
        config = spf.delay
    }

    override fun apply(ctx: Context) {
        val spf = spf.BlockType(ctx)
        spf.type = type
        spf.delay = config
    }
}

@Serializable
class CallerID : IConfig {
    var enabled = false
    var x = 0
    var y = 0
    var bgColor: Int = 0
    var template = ""
    override fun load(ctx: Context) {
        val spf = spf.CallerID(ctx)
        enabled = spf.isEnabled
        x = spf.x
        y = spf.y
        bgColor = spf.bgColor
        template = spf.template
    }

    override fun apply(ctx: Context) {
        val spf = spf.CallerID(ctx)
        spf.isEnabled = enabled
        spf.x = x
        spf.y = y
        spf.bgColor = bgColor
        spf.template = template
    }
}

@Serializable
class Notification : IConfig {
    var spamCallChannel = ""

    val channels = mutableListOf<Channel>()

    override fun load(ctx: Context) {
        val spf = spf.Notification(ctx)
        spamCallChannel = spf.spamCallChannelId
        channels.clear()
        channels.addAll(ChannelTable.listAll(ctx))
    }

    override fun apply(ctx: Context) {
        // 1. spf
        spf.Notification(ctx).apply {
            spamCallChannelId = spamCallChannel
        }
        // 2. Table and System channels
        ChannelTable.clearAll(ctx)
        deleteAllChannels(ctx)
        channels.forEach {
            ChannelTable.add(ctx, it)
            createChannel(ctx, it)
        }
    }
}

@Serializable
class Permissions : IConfig {
    var allEnabledNames = ""

    override fun load(ctx: Context) {
        allEnabledNames = Permission.allEnabled()
            .joinToString(",") { it::class.java.simpleName.substringAfterLast('$') }
    }

    override fun apply(ctx: Context) { }
}

@Serializable
class HistoryLogs : IConfig {
    val calls = mutableListOf<HistoryRecord>()

    override fun load(ctx: Context) {
        calls.clear()
        calls.addAll(CallTable().listRecords(ctx))
    }

    override fun apply(ctx: Context) {
        CallTable().clearAll(ctx)
        CallTable().addAll(ctx, calls)
    }
}

enum class Category(val labelId: Int) {
    OTHERS(R.string.others),
    LANGUAGE(R.string.language),
    THEME(R.string.theme),
    HISTORY_LOGS(R.string.history_logs),
}

@Serializable
data class CategorySelection(
    val all: Set<Category> = Category.entries
        .filter {
            // exclude call history records
            it != Category.HISTORY_LOGS
        }
        .toSet()
) {
    fun isSelected(category: Category): Boolean = category in all

    // Returns a new state with the category added to the selection.
    fun select(category: Category): CategorySelection {
        return copy(all = all + category)
    }

    // Returns a new state with the category removed from the selection.
    fun unselect(category: Category): CategorySelection {
        return copy(all = all - category)
    }

    fun toggle(category: Category): CategorySelection {
        return if (isSelected(category)) unselect(category) else select(category)
    }

    fun contains(category: Category): Boolean {
        return category in all
    }

    fun negate(): CategorySelection {
        return CategorySelection(allUnselected().toSet())
    }

    fun allSelected(): List<Category> = all.toList()

    fun allUnselected(): List<Category> = Category.entries.filter { it !in all }
}
val emptyCategorySelection by lazy {
    CategorySelection(emptySet())
}

@Serializable
class Configs {
    var categories: CategorySelection? = null

    var global: Global? = null
    var settingSections: SettingSections? = null
    var historyOptions: HistoryOptions? = null
    var language: Language? = null
    var theme: Theme? = null

    var contacts: Contact? = null
    var blockType: BlockType? = null
    var callerID: CallerID? = null
    var notification: Notification? = null
    var emergency: EmergencySituation? = null

    var historyLogs: HistoryLogs? = null

    var permissions: Permissions? = null

    // Read all settings from SharedPref/Database into this object, for saving to file.
    fun load(ctx: Context, categories: CategorySelection) {
        this.categories = categories

        if (categories.isSelected(Category.OTHERS)) {
            global = Global().also { it.load(ctx) }
            settingSections = SettingSections().also { it.load(ctx) }
            historyOptions = HistoryOptions().also { it.load(ctx) }
            contacts = Contact().also { it.load(ctx) }
            emergency = EmergencySituation().also { it.load(ctx) }
            blockType = BlockType().also { it.load(ctx) }
            notification = Notification().also { it.load(ctx) }
            callerID = CallerID().also { it.load(ctx) }
            permissions = Permissions().also { it.load(ctx) }
        }
        if (categories.isSelected(Category.LANGUAGE)) {
            language = Language().also { it.load(ctx) }
        }
        if (categories.isSelected(Category.THEME)) {
            theme = Theme().also { it.load(ctx) }
        }
        if (categories.isSelected(Category.HISTORY_LOGS)) {
            historyLogs = HistoryLogs().also { it.load(ctx) }
        }
    }

    // This object has been fulfilled, apply the values to SharedPref/Database
    fun apply(ctx: Context, categories: CategorySelection) {
        if (categories.isSelected(Category.OTHERS)) {
            global?.apply(ctx)
            settingSections?.apply(ctx)
            historyOptions?.apply(ctx)
            contacts?.apply(ctx)
            emergency?.apply(ctx)
            blockType?.apply(ctx)
            notification?.apply(ctx)
            callerID?.apply(ctx)
            permissions?.apply(ctx)
        }
        if (categories.isSelected(Category.LANGUAGE)) {
            language?.apply(ctx)
        }
        if (categories.isSelected(Category.THEME)) {
            theme?.apply(ctx)
        }
        if (categories.isSelected(Category.HISTORY_LOGS)) {
            historyLogs?.apply(ctx)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun compressToBytes(): ByteArray {
        val me = this
        return ByteArrayOutputStream(128 * 1024).use { baos ->  // optional initial size hint
            GZIPOutputStream(baos).apply {
                ConfigJson.encodeToStream(serializer(), me, this)
                finish()  // explicit finish (good practice)
            }
            baos.toByteArray()
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun decompressFromBytes(bytes: ByteArray): Configs {
            return ByteArrayInputStream(bytes).use { input ->
                GZIPInputStream(input).use { gzip ->
                    ConfigJson.decodeFromStream(
                        deserializer = Configs.serializer(),
                        stream = gzip
                    )
                }
            }
        }
    }
}
