package spam.blocker.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import spam.blocker.service.checker.ByContact
import spam.blocker.service.checker.ByDefault
import spam.blocker.service.checker.ByEmergencyCall
import spam.blocker.service.checker.ByEmergencySituation
import spam.blocker.service.checker.ByNonContact
import spam.blocker.service.checker.ByRegexRule
import spam.blocker.service.checker.ICheckResult

// This module contains ALL serializable classes in this app

val myModule = SerializersModule {
    // Check Results
    polymorphic(ICheckResult::class) {
        subclass(ByContact::class, ByContact.serializer())
        subclass(ByDefault::class, ByDefault.serializer())
        subclass(ByEmergencyCall::class, ByEmergencyCall.serializer())
        subclass(ByEmergencySituation::class, ByEmergencySituation.serializer())
        subclass(ByNonContact::class, ByNonContact.serializer())
        subclass(ByRegexRule::class, ByRegexRule.serializer())
    }
}

// A json serializer that supports all interfaces(ICheckResult, ...) in this app
val MyJson = Json {
    this.serializersModule = myModule
    encodeDefaults = true
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}

val MyPrettyJson = Json {
    prettyPrint = true

    this.serializersModule = myModule
    encodeDefaults = true
    classDiscriminator = "type"
    ignoreUnknownKeys = true
}
