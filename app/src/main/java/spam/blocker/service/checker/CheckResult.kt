package spam.blocker.service.checker

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import spam.blocker.R
import spam.blocker.db.Notification.Channel
import spam.blocker.db.Notification.ChannelTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.def.Def.DEFAULT_HANG_UP_DELAY
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_DEFAULT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_EMERGENCY_CALL
import spam.blocker.def.Def.RESULT_ALLOWED_BY_EMERGENCY_SITUATION
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CNAP_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_PREFIX_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_NUMBER_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CNAP_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTACT_PREFIX_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTACT_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NON_CONTACT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_DEFAULT
import spam.blocker.ui.widgets.GreyText
import spam.blocker.util.Notification.ShowType
import spam.blocker.util.Notification.missingChannel
import spam.blocker.util.PermissiveJson
import spam.blocker.util.spf

interface ICheckResult {
    val byType: Int

    // Used in the notification
    fun resultReasonStr(ctx: Context): String

    // This will be rendered in the history items as the reason.(2nd row)
    @Composable
    fun ResultReason(expanded: Boolean) {
        GreyText(
            text = resultReasonStr(LocalContext.current),
            fontSize = 16.sp,
            maxLines = if (expanded) 3 else 1,
        )
    }

    fun shouldBlock(): Boolean {
        return Def.isBlocked(byType)
    }

    // For "Answer + Hang up", returns the delay before "Hang Up"
    fun hangUpDelay(ctx: Context): Int {
        return spf.BlockType(ctx).delay.toIntOrNull() ?: DEFAULT_HANG_UP_DELAY
    }

    // Prepare the content to be saved in database, as the `HistoryTable.reason` column
    fun reasonToDb(): String {
        return ""
    }

    // The default block type when it's not overridden by per rule block type.
    fun getBlockType(ctx: Context): Int {
        return spf.BlockType(ctx).type
    }

    // The default notification channel for blocked calls
    fun getNotificationChannel(ctx: Context, showType: ShowType): Channel {
        val spf = spf.Notification(ctx)
        val channelId = spf.spamCallChannelId
        return ChannelTable.findByChannelId(ctx, channelId) ?: missingChannel()
    }
}

// passed by default
@Serializable
class ByDefault(
    override val byType: Int = RESULT_ALLOWED_BY_DEFAULT,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.passed_by_default)
    }
}

// allowed by emergency incoming call
@Serializable
class ByEmergencyCall(
    override val byType: Int = RESULT_ALLOWED_BY_EMERGENCY_CALL,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.emergency_call)
    }
}

// allowed by the Emergency in quick settings
@Serializable
class ByEmergencySituation(
    override val byType: Int = RESULT_ALLOWED_BY_EMERGENCY_SITUATION,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.emergency_situation)
    }
}

// allowed by contact, or blocked by non-contact
@Serializable
class ByContact(
    override val byType: Int = RESULT_ALLOWED_BY_CONTACT,
    private val contactName: String? = null,
) : ICheckResult {
    override fun reasonToDb(): String {
        return contactName ?: ""
    }

    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.contact)
    }
}

@Serializable
class ByNonContact(
    override val byType: Int = RESULT_BLOCKED_BY_NON_CONTACT,
) : ICheckResult {
    override fun resultReasonStr(ctx: Context): String {
        return ctx.getString(R.string.non_contact)
    }
}


// allowed/blocked by a regex rule
@Serializable
class ByRegexRule(
    override val byType: Int,
    val rule: RegexRule? = null, // null: the rule has been deleted
    val details: String? = null,
) : ICheckResult {

    @Serializable
    data class DbData(
        val ruleId: Long,
        val details: String? = null,
    )

    override fun reasonToDb(): String {
        return Json.encodeToString(DbData(rule?.id ?: 0, details))
    }

    override fun getBlockType(ctx: Context): Int {
        return rule?.blockType ?: super.getBlockType(ctx)
    }

    override fun hangUpDelay(ctx: Context): Int {
        return rule?.blockTypeConfig?.toIntOrNull() ?: DEFAULT_HANG_UP_DELAY
    }

    override fun getNotificationChannel(ctx: Context, showType: ShowType): Channel {
        if (rule == null) return super.getNotificationChannel(ctx, showType)
        return ChannelTable.findByChannelId(ctx, rule.channel) ?: missingChannel()
    }

    override fun resultReasonStr(ctx: Context): String {
        val summary = if (rule != null) {
            if (rule.description != "") rule.description else rule.patternStr()
        } else {
            ctx.resources.getString(R.string.deleted_filter)
        }

        return when (byType) {
            RESULT_ALLOWED_BY_NUMBER_REGEX, RESULT_BLOCKED_BY_NUMBER_REGEX ->
                ctx.getString(R.string.regex_pattern) + ": $summary"

            RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX, RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX ->
                ctx.getString(R.string.contact_group) + ": $summary"

            RESULT_ALLOWED_BY_CONTACT_REGEX, RESULT_BLOCKED_BY_CONTACT_REGEX ->
                ctx.getString(R.string.contact_rule) + ": $summary"

            RESULT_ALLOWED_BY_CNAP_REGEX, RESULT_BLOCKED_BY_CNAP_REGEX ->
                ctx.getString(R.string.caller_name) + ": $summary"

            RESULT_ALLOWED_BY_CONTACT_PREFIX_REGEX, RESULT_BLOCKED_BY_CONTACT_PREFIX_REGEX ->
                ctx.getString(R.string.contact_prefix) + ": $details"

            else -> ctx.getString(R.string.regex_pattern) + ": $summary"
        }
    }
}

// Rebuild the check result from the history record
fun parseCheckResultFromDb(ctx: Context, result: Int, reason: String): ICheckResult {
    return when (result) {
        RESULT_ALLOWED_BY_EMERGENCY_CALL -> ByEmergencyCall()
        RESULT_ALLOWED_BY_EMERGENCY_SITUATION -> ByEmergencySituation()

        RESULT_ALLOWED_BY_CONTACT -> ByContact(
            contactName = reason
        )
        RESULT_BLOCKED_BY_NON_CONTACT -> ByNonContact()

        RESULT_ALLOWED_BY_NUMBER_REGEX, RESULT_BLOCKED_BY_NUMBER_REGEX,
        RESULT_ALLOWED_BY_CNAP_REGEX, RESULT_BLOCKED_BY_CNAP_REGEX,
        RESULT_ALLOWED_BY_CONTACT_REGEX, RESULT_BLOCKED_BY_CONTACT_REGEX,
        RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX, RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX,
        RESULT_ALLOWED_BY_CONTACT_PREFIX_REGEX, RESULT_BLOCKED_BY_CONTACT_PREFIX_REGEX -> {
            val dbData = runCatching {
                PermissiveJson.decodeFromString<ByRegexRule.DbData>(reason)
            }.getOrNull()
            val rule = dbData?.ruleId?.let { id ->
                NumberRegexTable().listAll(ctx).firstOrNull { it.id == id }
            }
            ByRegexRule(byType = result, rule = rule, details = dbData?.details)
        }

        else -> ByDefault(result)
    }
}
