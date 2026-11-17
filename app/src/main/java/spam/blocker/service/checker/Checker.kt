package spam.blocker.service.checker

import android.content.Context
import android.telecom.Call
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.RegexRule
import spam.blocker.def.Def
import spam.blocker.ui.setting.regex.RegexMode.ModeType
import spam.blocker.util.A
import spam.blocker.util.Contacts
import spam.blocker.util.ILogger
import spam.blocker.util.Permission
import spam.blocker.util.TimeSchedule
import spam.blocker.util.Util
import spam.blocker.util.hasFlag
import spam.blocker.util.regexMatchesNumber
import spam.blocker.util.formatAnnotated
import spam.blocker.util.getSaveableOutput
import spam.blocker.util.spf

class CheckContext(
    var rawNumber: String,
    var cnap: String? = null,
    val callDetails: Call.Details? = null,
    val simSlot: Int?, // on which SIM slot is the call ringing on
    val logger: ILogger? = null,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val checkers: List<IChecker>,
    var anythingWrong: Boolean = false,
)

interface IChecker {
    fun priority(): Int
    fun check(cCtx: CheckContext): ICheckResult?
    fun desc(): AnnotatedString

    // These functions are only for detecting priority conflicts in the current setup.
    fun listType(): Boolean? = true // true: whitelist, false: blacklist, null: unknown
    fun isConfigEnabled(): Boolean

    // A checker may be applied only for call or only for Sms in the original design, here only call.
    fun isConfigEnabledForCall(): Boolean = true
    fun isConfigEnabledForSms(): Boolean = false

    fun logChecking(ctx: Context, logger: ILogger?) {
        logger?.debug(
            buildAnnotatedString {
                appendInlineContent(id = "priority")
                append(
                    ctx.getString(R.string.checking_template_new)
                        .formatAnnotated(
                            if (priority() == Int.MAX_VALUE) {
                                ctx.getString(R.string.max).A(G.palette.priority)
                            } else {
                                priority().toString().A(G.palette.priority)
                            },
                            desc()
                        )
                )
            }
        )
    }
}

// Find priority conflicts in a List<IChecker>
fun List<IChecker>.findConflicts(): List<IChecker> {
    val grouped = this.groupBy { it.priority() }

    val conflicting = mutableListOf<IChecker>()

    for ((_, group) in grouped) {
        // Separate checkers by their listType
        val trueCheckers = group.filter { it.listType() == true }
        val falseCheckers = group.filter { it.listType() == false }
        // null ones are ignored completely

        // If both true and false exist in same priority → conflict
        if (trueCheckers.isNotEmpty() && falseCheckers.isNotEmpty()) {
            conflicting.addAll(trueCheckers)
            conflicting.addAll(falseCheckers)
        }
    }

    return conflicting
}

class Checker { // for namespace only

    class PassedByDefault(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabled() = true

        override fun desc() =
            ctx.getString(R.string.passed_by_default).A(G.palette.infoBlue)

        override fun priority(): Int {
            return Int.MIN_VALUE
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            cCtx.logger?.success(ctx.getString(R.string.passed_by_default))
            return ByDefault()
        }
    }

    // This checks if the incoming call is from an emergency number.
    // It's always enabled, there is no setting entry for this.
    private class EmergencyCall(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabled() = true

        override fun desc() =
            ctx.getString(R.string.emergency_call).A(G.palette.infoBlue)

        override fun priority(): Int {
            return Int.MAX_VALUE
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            val C = G.palette

            val logger = cCtx.logger
            val callDetails = cCtx.callDetails

            logChecking(ctx, logger)

            if (callDetails == null) { // there is no callDetail when testing
                logger?.debug(ctx.getString(R.string.skip_for_testing).A(C.disabled))
                return null
            }

            if (callDetails.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)
                || callDetails.hasProperty(Call.Details.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL)
                || Util.isEmergencyNumber(ctx, cCtx.rawNumber)
            ) {
                logger?.success(
                    ctx.getString(R.string.allowed_by)
                        .formatAnnotated(desc())
                )
                return ByEmergencyCall()
            }

            return null
        }
    }

    // This is the `Emergency` in Quick Settings, it allows all calls after calling an emergency number.
    private class EmergencySituation(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabled() = spf.EmergencySituation(ctx).isEnabled

        override fun desc() =
            ctx.getString(R.string.emergency_situation).A(G.palette.infoBlue)

        override fun priority(): Int {
            return spf.EmergencySituation(ctx).priority
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            val spf = spf.EmergencySituation(ctx)
            if (!spf.isEnabled)
                return null

            val logger = cCtx.logger
            logChecking(ctx, logger)

            // 1. check time
            val lastEccCallTime: Long = spf.timestamp
            val duration: Long = (spf.duration * 60 * 1000).toLong()
            val now = System.currentTimeMillis()
            if (lastEccCallTime + duration < now) {
                return null
            }

            logger?.success(
                ctx.getString(R.string.allowed_by)
                    .formatAnnotated(desc())
            )
            return ByEmergencySituation()
        }
    }

    private class Contact(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabled() = spf.Contact(ctx).isEnabled
        override fun priority(): Int {
            return spf.Contact(ctx).lenientPriority
        }

        override fun desc() =
            ctx.getString(R.string.contact).A(G.palette.infoBlue)

        override fun check(cCtx: CheckContext): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!isConfigEnabled() || !Permission.contacts.isGranted) {
                return null
            }

            logChecking(ctx, logger)

            val contact = Contacts.findContactByRawNumber(ctx, rawNumber)
            if (contact != null) { // is contact
                logger?.success(
                    ctx.getString(R.string.allowed_by).formatAnnotated(desc())
                            + ": ${contact.name}".A()
                )
                return ByContact(Def.RESULT_ALLOWED_BY_CONTACT, contact.name)
            }
            return null
        }
    }

    // The "Contacts" in quick settings.
    // It checks whether the phone number is unknown(not from a contact).
    private class NonContact(
        private val ctx: Context,
    ) : IChecker {
        override fun isConfigEnabled(): Boolean {
            val spf = spf.Contact(ctx)
            return spf.isEnabled && spf.isStrict
        }
        override fun listType() = false

        override fun desc() =
            ctx.getString(R.string.non_contact).A(G.palette.infoBlue)

        override fun priority(): Int {
            return spf.Contact(ctx).strictPriority
        }

        override fun check(cCtx: CheckContext): ICheckResult? {
            val rawNumber = cCtx.rawNumber
            val logger = cCtx.logger

            if (!Permission.contacts.isGranted) {
                return null
            }
            if (!isConfigEnabled()) {
                return null
            }

            logChecking(ctx, logger)

            val contact = Contacts.findContactByRawNumber(ctx, rawNumber)
            if (contact == null) { // not from contacts
                logger?.error(
                    ctx.getString(R.string.blocked_by_template).formatAnnotated(
                        desc()
                    )
                )
                return ByNonContact()
            }
            return null
        }
    }


    abstract class RegexRuleChecker(
        val ctx: Context,
        var rule: RegexRule,
        val labelId: Int
    ) : IChecker {
        override fun isConfigEnabledForCall() = rule.flags.hasFlag(Def.FLAG_FOR_CALL)
        override fun isConfigEnabledForSms() = false
        override fun isConfigEnabled() = isConfigEnabledForCall()
        override fun listType() = rule.isWhitelist()
        override fun priority(): Int = rule.priority

        override fun desc(): AnnotatedString {
            val C = G.palette
            return ctx.getString(R.string.label_value_pair).formatAnnotated(
                ctx.getString(labelId).A(C.infoBlue),
                rule.descOrPattern().A(if (rule.isBlacklist) C.error else C.success)
            )
        }

        open fun isEnabled(cCtx: CheckContext): Boolean {
            // 1. only for call
            if (!isConfigEnabledForCall()) return false
            // 2. time schedule
            if (TimeSchedule.dissatisfyNow(rule.schedule)) return false
            // 3. sim slot
            if (rule.simSlot != null && cCtx.simSlot != null) {
                if (!Permission.phoneState.isGranted) {
                    cCtx.logger?.warn(
                        ctx.getString(R.string.missing_permission)
                            .formatAnnotated(Permission.phoneState.name.A(G.palette.teal200))
                    )
                } else if (rule.simSlot != cCtx.simSlot) {
                    return false
                }
            }
            return true
        }

        // Log and build the result
        fun result(
            cCtx: CheckContext,
            blockedType: Int,
            allowedType: Int,
            extra: String = "",
            details: String? = null,
        ): ICheckResult {
            val block = rule.isBlacklist
            val text = (
                if (block) ctx.getString(R.string.blocked_by_template)
                else ctx.getString(R.string.allowed_by)
            ).formatAnnotated(desc())
            val extraStr = if (extra.isEmpty()) "" else " - $extra"
            if (block) cCtx.logger?.error(text + extraStr.A())
            else cCtx.logger?.success(text + extraStr.A())

            return ByRegexRule(
                byType = if (block) blockedType else allowedType,
                rule = rule,
                details = details,
            )
        }
    }

    // Matches the incoming number with the regex pattern
    class Number(
        ctx: Context,
        rule: RegexRule,
    ) : RegexRuleChecker(ctx, rule, R.string.number_rule) {
        override fun check(cCtx: CheckContext): ICheckResult? {
            if (!isEnabled(cCtx)) return null
            logChecking(ctx, cCtx.logger)

            if (!rule.pattern.regexMatchesNumber(ctx, cCtx.rawNumber, rule.patternFlags)) {
                return null
            }
            return result(
                cCtx,
                Def.RESULT_BLOCKED_BY_NUMBER_REGEX,
                Def.RESULT_ALLOWED_BY_NUMBER_REGEX,
            )
        }
    }

    // Matches the caller display name (CNAP)
    class CNAP(
        ctx: Context,
        rule: RegexRule,
    ) : RegexRuleChecker(ctx, rule, R.string.caller_name_rule) {
        override fun check(cCtx: CheckContext): ICheckResult? {
            if (!isEnabled(cCtx)) return null
            logChecking(ctx, cCtx.logger)

            val cnap = cCtx.cnap ?: return null
            if (!rule.pattern.regexMatchesNumber(ctx, cnap, rule.patternFlags)) {
                return null
            }
            return result(
                cCtx,
                Def.RESULT_BLOCKED_BY_CNAP_REGEX,
                Def.RESULT_ALLOWED_BY_CNAP_REGEX,
            )
        }
    }

    // Matches the contact name of the incoming number
    class RegexContact(
        ctx: Context,
        rule: RegexRule,
    ) : RegexRuleChecker(ctx, rule, R.string.contact_rule) {
        override fun check(cCtx: CheckContext): ICheckResult? {
            if (!Permission.contacts.isGranted) return null
            if (!isEnabled(cCtx)) return null
            logChecking(ctx, cCtx.logger)

            val contact = Contacts.findContactByRawNumber(ctx, cCtx.rawNumber) ?: return null
            if (!rule.matches(contact.name)) return null

            return result(
                cCtx,
                Def.RESULT_BLOCKED_BY_CONTACT_REGEX,
                Def.RESULT_ALLOWED_BY_CONTACT_REGEX,
            )
        }
    }

    // Matches the contact group name of the incoming number
    class ContactGroup(
        ctx: Context,
        rule: RegexRule,
    ) : RegexRuleChecker(ctx, rule, R.string.contact_group) {
        override fun check(cCtx: CheckContext): ICheckResult? {
            if (!Permission.contacts.isGranted) return null
            if (!isEnabled(cCtx)) return null
            logChecking(ctx, cCtx.logger)

            val matched = Contacts.findGroupsContainNumber(ctx, cCtx.rawNumber)
                .firstOrNull { rule.matches(it) } ?: return null

            return result(
                cCtx,
                Def.RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX,
                Def.RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX,
            )
        }
    }

    // Fuzzy prefix match: both the incoming number and the contact number match the regex
    class ContactPrefix(
        ctx: Context,
        rule: RegexRule,
    ) : RegexRuleChecker(ctx, rule, R.string.contact_prefix) {
        override fun check(cCtx: CheckContext): ICheckResult? {
            if (!Permission.contacts.isGranted) return null
            if (!isEnabled(cCtx)) return null
            logChecking(ctx, cCtx.logger)

            if (!rule.pattern.regexMatchesNumber(ctx, cCtx.rawNumber, rule.patternFlags)) {
                return null
            }
            val contact = Contacts.findContactByNumberPrefix(
                ctx, cCtx.rawNumber, rule.pattern, rule.patternFlags
            ) ?: return null

            return result(
                cCtx,
                Def.RESULT_BLOCKED_BY_CONTACT_PREFIX_REGEX,
                Def.RESULT_ALLOWED_BY_CONTACT_PREFIX_REGEX,
                extra = contact.name,
                details = contact.name,
            )
        }
    }

    companion object {
        fun defaultCallCheckers(ctx: Context): List<IChecker> {
            val checkers = arrayListOf(
                PassedByDefault(ctx),
                EmergencyCall(ctx),
                EmergencySituation(ctx),
                Contact(ctx),
                NonContact(ctx),
            )

            // Add the user defined number rules as checkers
            checkers += NumberRegexTable().listAll(ctx).map {
                it.numberRuleToChecker(ctx)
            }
            return checkers
        }

        // Return value: Triple< check_result, full_log, anything_went_wrong >
        fun checkCall(
            ctx: Context,
            rawNumber: String,
            cnap: String? = null,
            callDetails: Call.Details? = null,
            simSlot: Int? = null,
            logger: ILogger? = null,
            checkers: List<IChecker> = defaultCallCheckers(ctx),
        ): Triple<ICheckResult, String?, Boolean> {
            val cCtx = CheckContext(
                rawNumber = rawNumber,
                cnap = cnap,
                callDetails = callDetails,
                logger = logger,
                checkers = checkers,
                simSlot = simSlot,
            )

            // sort by priority desc
            val sortedCheckers = checkers.sortedByDescending {
                it.priority()
            }

            // Try all checkers in order, until a match is found
            // There will definitely be a match, as the last checker is PassedByDefault and it always allows.
            val result = sortedCheckers.firstNotNullOf {
                it.check(cCtx)
            }
            val fullScreeningLog = logger?.getSaveableOutput()?.serialize() ?: ""

            return Triple(
                result, fullScreeningLog, cCtx.anythingWrong
            )
        }
    }
}

fun RegexRule.numberRuleToChecker(ctx: Context): IChecker {
    return when (patternModeType) {
        ModeType.ContactName -> Checker.RegexContact(ctx, this)
        ModeType.ContactGroup -> Checker.ContactGroup(ctx, this)
        ModeType.ContactPrefix -> Checker.ContactPrefix(ctx, this)
        ModeType.CallerName -> Checker.CNAP(ctx, this)
        else -> Checker.Number(ctx, this)
    }
}
