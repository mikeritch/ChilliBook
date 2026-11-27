package uk.chillibook.diary

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException

internal object WearableHealthConnect {
    private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
    val permissions: Set<String> = setOf(stepsPermission, sleepPermission)

    fun status(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun granted(context: Context): Set<String> =
        if (status(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
                .intersect(permissions)
        } else {
            emptySet()
        }

    suspend fun read(
        context: Context,
        granted: Set<String>,
        end: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): List<WearableReading> {
        if (permissions.none(granted::contains)) return emptyList()
        val client = HealthConnectClient.getOrCreate(context)
        val start = end.minusDays(29)
        val now = Instant.now()
        val days = sortedMapOf<LocalDate, WearableReading>()

        if (stepsPermission in granted) {
            val buckets = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        start.atStartOfDay(), end.plusDays(1).atStartOfDay()
                    ),
                    timeRangeSlicer = Period.ofDays(1)
                )
            )
            for (bucket in buckets) {
                val steps = bucket.result[StepsRecord.COUNT_TOTAL] ?: continue
                if (steps !in 0..Int.MAX_VALUE.toLong()) continue
                val date = bucket.startTime.toLocalDate()
                days[date] = WearableReading(
                    date = date.toString(),
                    steps = steps.toInt(),
                    stepsSource = sourceLabel(context, bucket.result.dataOrigins.singleOrNull()?.packageName)
                )
            }
            if (days.isNotEmpty()) {
                val hours = try {
                    readStepHours(client, start, end, zone, now)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Providers may reject the optional hourly query. Keep the daily readings.
                    emptyMap()
                }
                days.replaceAll { date, reading -> reading.copy(stepHours = hours[date]) }
            }
        }

        if (sleepPermission in granted) {
            // Read from the preceding evening so a session can belong to the first morning.
            // Use an instant cutoff to stay within the minimum access window on a new grant.
            val from = maxOf(start.minusDays(1).atStartOfDay(zone).toInstant(), now.minus(Duration.ofDays(30)))
            val until = minOf(end.plusDays(2).atStartOfDay(zone).toInstant(), now)
            if (from < until) {
                val sessions = readSleep(client, TimeRangeFilter.between(from, until))
                    .filter { it.endTime <= now }
                for ((date, periods) in sleepPeriodsByWakeDate(sessions, start, end, zone)) {
                    val minutes = periods.fold(Duration.ZERO) { total, session ->
                        total.plus(sleepDuration(session))
                    }.toMinutes()
                    if (minutes !in 1..Int.MAX_VALUE.toLong()) continue
                    val reading = days[date] ?: WearableReading(date.toString())
                    days[date] = reading.copy(
                        sleepMinutes = minutes.toInt(),
                        sleepSource = sourceLabel(
                            context, periods.map { it.metadata.dataOrigin.packageName }.distinct().singleOrNull()
                        ),
                        sleepPeriods = periods.map { session ->
                            WearableSleepPeriod(
                                detail = recordedSleep(session, zone),
                                source = sourceLabel(context, session.metadata.dataOrigin.packageName)
                            )
                        }
                    )
                }
            }
        }
        return days.values.toList()
    }

    private suspend fun readStepHours(
        client: HealthConnectClient,
        start: LocalDate,
        end: LocalDate,
        zone: ZoneId,
        now: Instant
    ): Map<LocalDate, List<StepHour>> {
        // Stay within the minimum grant window and start the buckets on a clock hour.
        val cutoff = now.minus(Duration.ofDays(30)).atZone(zone)
        val cutoffHour = cutoff.truncatedTo(ChronoUnit.HOURS)
        val firstFullHour = if (cutoff == cutoffHour) cutoffHour else cutoffHour.plusHours(1)
        val from = maxOf(start.atStartOfDay(zone).toInstant(), firstFullHour.toInstant())
        val until = minOf(end.plusDays(1).atStartOfDay(zone).toInstant(), now)
        if (from >= until) return emptyMap()
        val buckets = client.aggregateGroupByDuration(
            AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from, until),
                timeRangeSlicer = Duration.ofHours(1)
            )
        )
        return stepHoursByDate(
            buckets.mapNotNull { bucket ->
                bucket.result[StepsRecord.COUNT_TOTAL]?.let { bucket.startTime to it }
            },
            zone
        )
    }

    private suspend fun readSleep(
        client: HealthConnectClient,
        timeRange: TimeRangeFilter
    ): List<SleepSessionRecord> = buildList {
        var pageToken: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = timeRange,
                    pageToken = pageToken
                )
            )
            addAll(page.records)
            pageToken = page.pageToken
        } while (!pageToken.isNullOrEmpty())
    }

    private fun sourceLabel(context: Context, packageName: String?): String {
        if (packageName == "com.xiaomi.wearable") return "Mi Fitness"
        if (packageName.isNullOrBlank()) return "Health Connect"
        val manager = context.packageManager
        return try {
            manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0))
                .toString().trim().take(80).ifBlank { "Health Connect" }
        } catch (_: PackageManager.NameNotFoundException) {
            "Health Connect"
        }
    }
}

internal fun stepHoursByDate(
    buckets: Iterable<Pair<Instant, Long>>,
    zone: ZoneId
): Map<LocalDate, List<StepHour>> {
    val countsByDate = mutableMapOf<LocalDate, MutableMap<Int, Long>>()
    for ((start, count) in buckets) {
        if (count !in 0..Int.MAX_VALUE.toLong()) continue
        val clock = start.atZone(zone)
        // Both occurrences of an hour on the autumn clock change belong to that clock hour.
        countsByDate.getOrPut(clock.toLocalDate()) { sortedMapOf() }
            .merge(clock.hour, count, Long::plus)
    }
    return countsByDate.mapValues { (_, hours) ->
        hours.mapNotNull { (hour, count) ->
            count.takeIf { it <= Int.MAX_VALUE.toLong() }?.let { StepHour(hour, it.toInt()) }
        }
    }.filterValues { it.isNotEmpty() }
}

internal fun sleepPeriodsByWakeDate(
    sessions: Iterable<SleepSessionRecord>,
    first: LocalDate,
    last: LocalDate,
    zone: ZoneId
): Map<LocalDate, List<SleepSessionRecord>> {
    val priority = compareByDescending<SleepSessionRecord> { sleepDuration(it) }
        .thenByDescending { it.endTime }
        .thenBy { it.metadata.dataOrigin.packageName }
        .thenBy { it.metadata.id }
    val selected = mutableListOf<SleepSessionRecord>()
    for (session in sessions.sortedWith(priority)) {
        if (selected.none { it.startTime < session.endTime && session.startTime < it.endTime }) {
            selected.add(session)
        }
    }
    return selected.groupBy { session ->
        session.endTime.atZone(session.endZoneOffset ?: zone).toLocalDate()
    }.filterKeys { it in first..last }.mapValues { (_, periods) ->
        periods.take(MAX_SLEEP_PERIODS).sortedBy { it.startTime }
    }
}

internal fun sleepDuration(session: SleepSessionRecord): Duration =
    Duration.between(session.startTime, session.endTime)

internal fun recordedSleep(session: SleepSessionRecord, zone: ZoneId): RecordedSleep {
    val start = session.startTime.atOffset(session.startZoneOffset ?: zone.rules.getOffset(session.startTime))
    val end = session.endTime.atOffset(session.endZoneOffset ?: zone.rules.getOffset(session.endTime))
    val sessionSeconds = sleepDuration(session).seconds.coerceAtMost(Int.MAX_VALUE.toLong())
    val phases = buildList {
        var previousEnd = 0
        for (stage in session.stages.sortedBy { it.startTime }) {
            if (size == MAX_SLEEP_PHASES) break
            val startSecond = Duration.between(session.startTime, stage.startTime).seconds
                .coerceIn(previousEnd.toLong(), sessionSeconds).toInt()
            val endSecond = Duration.between(session.startTime, stage.endTime).seconds
                .coerceIn(0, sessionSeconds).toInt()
            if (endSecond <= startSecond) continue
            val kind = when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "awake"
                SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
                SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
                SleepSessionRecord.STAGE_TYPE_REM -> "rem"
                SleepSessionRecord.STAGE_TYPE_SLEEPING -> "asleep"
                else -> "unknown"
            }
            add(SleepPhase(startSecond, endSecond, kind))
            previousEnd = endSecond
        }
    }
    return RecordedSleep(start.toString(), end.toString(), phases)
}
