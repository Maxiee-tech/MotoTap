package com.example.mototap.core.util

import java.util.Calendar
import java.util.TimeZone

data class DayHours(
    val open: String = "08:00",
    val close: String = "18:00",
    val closed: Boolean = false,
)

data class WorkingHours(
    val timezone: String = DEFAULT_TIMEZONE,
    val days: Map<String, DayHours> = emptyMap(),
)

data class OpenClosedStatus(
    val isOpen: Boolean,
    val label: String,
)

private val TIME_RE = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")

val WEEKDAY_KEYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

val WEEKDAY_LABELS = mapOf(
    "mon" to "Monday",
    "tue" to "Tuesday",
    "wed" to "Wednesday",
    "thu" to "Thursday",
    "fri" to "Friday",
    "sat" to "Saturday",
    "sun" to "Sunday",
)

const val DEFAULT_TIMEZONE = "Africa/Nairobi"

fun isValidTime24h(value: String?): Boolean =
    !value.isNullOrBlank() && TIME_RE.matches(value.trim())

fun defaultWorkingHours(): WorkingHours {
    val weekday = DayHours(open = "08:00", close = "18:00", closed = false)
    val saturday = DayHours(open = "08:00", close = "13:00", closed = false)
    val sunday = DayHours(open = "00:00", close = "00:00", closed = true)
    return WorkingHours(
        timezone = DEFAULT_TIMEZONE,
        days = mapOf(
            "mon" to weekday,
            "tue" to weekday,
            "wed" to weekday,
            "thu" to weekday,
            "fri" to weekday,
            "sat" to saturday,
            "sun" to sunday,
        ),
    )
}

fun normalizeDayHours(raw: Any?): DayHours {
    val map = raw as? Map<*, *> ?: return DayHours(closed = true)
    val closed = map["closed"] == true
    val open = map["open"]?.toString()?.trim().orEmpty()
    val close = map["close"]?.toString()?.trim().orEmpty()
    return DayHours(
        open = if (isValidTime24h(open)) open else "08:00",
        close = if (isValidTime24h(close)) close else "18:00",
        closed = closed,
    )
}

fun normalizeWorkingHours(raw: Any?): WorkingHours? {
    val map = raw as? Map<*, *> ?: return null
    val days = LinkedHashMap<String, DayHours>()
    var anyOpen = false
    for (key in WEEKDAY_KEYS) {
        val day = normalizeDayHours(map[key])
        days[key] = day
        if (!day.closed) anyOpen = true
    }
    if (!anyOpen) return null
    val timezone = map["timezone"]?.toString()?.trim().orEmpty().ifEmpty { DEFAULT_TIMEZONE }
    return WorkingHours(timezone = timezone, days = days)
}

fun hasValidWorkingHours(raw: Any?): Boolean {
    val hours = when (raw) {
        is WorkingHours -> raw
        else -> normalizeWorkingHours(raw)
    } ?: return false
    return WEEKDAY_KEYS.any { key ->
        val day = hours.days[key] ?: return@any false
        if (day.closed) return@any false
        val openMin = parseMinutes(day.open) ?: return@any false
        val closeMin = parseMinutes(day.close) ?: return@any false
        closeMin > openMin
    }
}

fun validateWorkingHours(hours: WorkingHours?): String? {
    if (hours == null || !hasValidWorkingHours(hours)) {
        return "Set working hours for at least one day."
    }
    for (key in WEEKDAY_KEYS) {
        val day = hours.days[key] ?: continue
        if (day.closed) continue
        if (!isValidTime24h(day.open) || !isValidTime24h(day.close)) {
            return "${WEEKDAY_LABELS[key]}: use 24-hour times (e.g. 08:00)."
        }
        val openMin = parseMinutes(day.open) ?: return "${WEEKDAY_LABELS[key]}: invalid open time."
        val closeMin = parseMinutes(day.close) ?: return "${WEEKDAY_LABELS[key]}: invalid close time."
        if (closeMin <= openMin) {
            return "${WEEKDAY_LABELS[key]}: closing time must be after opening time."
        }
    }
    return null
}

fun workingHoursToFirestoreMap(hours: WorkingHours): Map<String, Any> {
    val out = LinkedHashMap<String, Any>()
    out["timezone"] = hours.timezone.ifBlank { DEFAULT_TIMEZONE }
    for (key in WEEKDAY_KEYS) {
        val day = hours.days[key] ?: DayHours(closed = true)
        out[key] = mapOf(
            "open" to day.open,
            "close" to day.close,
            "closed" to day.closed,
        )
    }
    return out
}

fun getOpenClosedStatus(hours: WorkingHours?, nowMillis: Long = System.currentTimeMillis()): OpenClosedStatus {
    if (hours == null || !hasValidWorkingHours(hours)) {
        return OpenClosedStatus(isOpen = false, label = "Hours not set")
    }
    val zone = runCatching { TimeZone.getTimeZone(hours.timezone) }.getOrNull()
        ?: TimeZone.getTimeZone(DEFAULT_TIMEZONE)
    val cal = Calendar.getInstance(zone).apply { timeInMillis = nowMillis }
    val key = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "mon"
        Calendar.TUESDAY -> "tue"
        Calendar.WEDNESDAY -> "wed"
        Calendar.THURSDAY -> "thu"
        Calendar.FRIDAY -> "fri"
        Calendar.SATURDAY -> "sat"
        else -> "sun"
    }
    val today = hours.days[key]
    if (today == null || today.closed) {
        return OpenClosedStatus(isOpen = false, label = "Closed")
    }
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    val openMin = parseMinutes(today.open)
    val closeMin = parseMinutes(today.close)
    if (openMin == null || closeMin == null) {
        return OpenClosedStatus(isOpen = false, label = "Closed")
    }
    return when {
        nowMin in openMin until closeMin ->
            OpenClosedStatus(isOpen = true, label = "Open · closes ${today.close}")
        nowMin < openMin ->
            OpenClosedStatus(isOpen = false, label = "Closed · opens ${today.open}")
        else -> OpenClosedStatus(isOpen = false, label = "Closed")
    }
}

/** True when this account is allowed to set shop hours (garage owner or parts dealer — not joined staff). */
fun canManageWorkingHours(roleName: String?, garageRole: String?): Boolean {
    val role = roleName?.trim()?.lowercase().orEmpty()
    val isBusiness = role == "mechanic" || role == "parts_dealer" || role == "parts dealer"
    if (!isBusiness) return false
    // Joined garage mechanics inherit the owner's hours and must not edit them.
    val memberRole = garageRole?.trim()?.lowercase().orEmpty()
    if (memberRole == "mechanic") return false
    return true
}

/** Garage owners and parts dealers must publish weekly working hours; joined staff do not. */
fun needsWorkingHoursUpdate(
    roleName: String?,
    garageRole: String?,
    workingHours: WorkingHours?,
): Boolean {
    if (!canManageWorkingHours(roleName, garageRole)) return false
    return !hasValidWorkingHours(workingHours)
}

private fun parseMinutes(value: String): Int? {
    if (!isValidTime24h(value)) return null
    val parts = value.trim().split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return h * 60 + m
}
