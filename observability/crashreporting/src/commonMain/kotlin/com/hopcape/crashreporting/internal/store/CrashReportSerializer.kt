package com.hopcape.crashreporting.internal.store

import com.hopcape.crashreporting.api.DeviceContext
import com.hopcape.crashreporting.internal.model.Breadcrumb
import com.hopcape.crashreporting.internal.model.CrashReport

// ─────────────────────────────────────────────────────────────
// CrashReportSerializer — converts a CrashReport to/from a JSON
// string via MiniJson. Lives in commonMain so the platform file
// stores (Android's DiskCrashFileStore, plus the in-memory store)
// all persist the same format; only the raw byte IO is platform
// specific.
//
// Robustness over fidelity: fromJson returns null rather than
// throwing on malformed/partial input (a report half-written when
// the process was killed), so one corrupt file never blocks reading
// the others. Numeric custom-key values round-trip as Long/Double;
// exotic types degrade to their string form — acceptable, since
// custom keys are informational and vendors stringify them anyway.
// ─────────────────────────────────────────────────────────────
internal object CrashReportSerializer {

    fun toJson(report: CrashReport): String {
        val map = linkedMapOf<String, Any?>(
            "crashId" to report.crashId,
            "timestampMs" to report.timestampMs,
            "throwableType" to report.throwableType,
            "throwableMessage" to report.throwableMessage,
            "stackTrace" to report.stackTrace,
            "isFatal" to report.isFatal,
            "traceId" to report.traceId,
            "sessionId" to report.sessionId,
            "breadcrumbs" to report.breadcrumbs.map {
                linkedMapOf<String, Any?>("timestampMs" to it.timestampMs, "tag" to it.tag, "message" to it.message)
            },
            "customKeys" to report.customKeys,
            "device" to linkedMapOf<String, Any?>(
                "appVersion" to report.deviceContext.appVersion,
                "osVersion" to report.deviceContext.osVersion,
                "deviceModel" to report.deviceContext.deviceModel,
                "platform" to report.deviceContext.platform,
                "availableMemoryMb" to report.deviceContext.availableMemoryMb,
                "batteryLevel" to report.deviceContext.batteryLevel,
                "networkType" to report.deviceContext.networkType,
            ),
        )
        return MiniJson.encode(map)
    }

    @Suppress("UNCHECKED_CAST")
    fun fromJson(text: String): CrashReport? = runCatching {
        val root = MiniJson.decode(text) as Map<String, Any?>
        val device = root["device"] as Map<String, Any?>
        val crumbs = (root["breadcrumbs"] as List<Map<String, Any?>>).map {
            Breadcrumb(
                timestampMs = (it["timestampMs"] as Number).toLong(),
                tag = it["tag"] as String,
                message = it["message"] as String,
            )
        }
        CrashReport(
            crashId = root["crashId"] as String,
            timestampMs = (root["timestampMs"] as Number).toLong(),
            throwableType = root["throwableType"] as String,
            throwableMessage = root["throwableMessage"] as String?,
            stackTrace = root["stackTrace"] as String,
            isFatal = root["isFatal"] as Boolean,
            traceId = root["traceId"] as String?,
            sessionId = root["sessionId"] as String?,
            breadcrumbs = crumbs,
            customKeys = (root["customKeys"] as Map<String, Any?>),
            deviceContext = DeviceContext(
                appVersion = device["appVersion"] as String,
                osVersion = device["osVersion"] as String,
                deviceModel = device["deviceModel"] as String,
                platform = device["platform"] as String,
                availableMemoryMb = (device["availableMemoryMb"] as Number).toLong(),
                batteryLevel = (device["batteryLevel"] as Number).toInt(),
                networkType = device["networkType"] as String,
            ),
        )
    }.getOrNull()
}
