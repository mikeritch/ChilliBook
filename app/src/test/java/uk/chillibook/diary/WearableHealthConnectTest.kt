package uk.chillibook.diary

import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class WearableHealthConnectTest {
    private val london = ZoneId.of("Europe/London")

    @Test
    fun overnightSessionWinsOverOverlapAndKeepsNapAcrossClockChange() {
        val date = LocalDate.parse("2026-03-29")
        val overnight = sleep("2026-03-28T23:30:00Z", "2026-03-29T06:30:00Z")
        val overlap = sleep("2026-03-29T00:00:00Z", "2026-03-29T06:00:00Z")
        val nap = sleep("2026-03-29T13:00:00Z", "2026-03-29T14:00:00Z")

        val selected = sleepPeriodsByWakeDate(listOf(nap, overnight, overlap), date, date, london)

        assertEquals(mapOf(date to listOf(overnight, nap)), selected)
        assertEquals(selected, sleepPeriodsByWakeDate(listOf(overlap, overnight, nap), date, date, london))
        assertEquals(480L, selected.getValue(date).sumOf { sleepDuration(it).toMinutes() })
        assertEquals(
            RecordedSleep("2026-03-28T23:30Z", "2026-03-29T07:30+01:00"),
            recordedSleep(selected.getValue(date).first(), london)
        )
    }

    @Test
    fun recordedWakeOffsetAssignsShortSleepToCorrectDay() {
        val date = LocalDate.parse("2026-04-11")
        val session = sleep("2026-04-10T19:30:00Z", "2026-04-10T22:30:00Z", ZoneOffset.ofHours(2))
        val shortSleep = sleep("2026-04-10T22:00:00Z", "2026-04-10T23:00:00Z", ZoneOffset.ofHours(2))
        val nextDay = sleep("2026-04-11T22:00:00Z", "2026-04-11T23:00:00Z", ZoneOffset.ofHours(2))

        assertEquals(
            mapOf(date to listOf(session)),
            sleepPeriodsByWakeDate(listOf(session, nextDay), date, date, london)
        )
        assertEquals(
            mapOf(date to listOf(shortSleep)),
            sleepPeriodsByWakeDate(listOf(shortSleep), date, date, london)
        )
    }

    @Test
    fun overlapCrossingMidnightIsCountedOnceAndTouchingIntervalsAreKept() {
        val date = LocalDate.parse("2026-04-11")
        val earlierWake = sleep("2026-04-10T20:00:00Z", "2026-04-10T22:30:00Z")
        val overnight = sleep("2026-04-10T22:00:00Z", "2026-04-11T06:00:00Z")
        val adjacent = sleep("2026-04-11T06:00:00Z", "2026-04-11T07:00:00Z")

        assertEquals(
            mapOf(date to listOf(overnight, adjacent)),
            sleepPeriodsByWakeDate(listOf(adjacent, earlierWake, overnight), date.minusDays(1), date, london)
        )
    }

    @Test
    fun hourlyStepsSumRepeatedClockHourAndPreserveZeroAndGaps() {
        val date = LocalDate.parse("2026-10-25")
        val buckets = listOf(
            Instant.parse("2026-10-25T04:00:00Z") to 0L,
            Instant.parse("2026-10-25T00:00:00Z") to 20L,
            Instant.parse("2026-10-25T01:00:00Z") to 35L
        )

        assertEquals(
            mapOf(date to listOf(StepHour(1, 55), StepHour(4, 0))),
            stepHoursByDate(buckets, london)
        )
    }

    @Test
    fun detailKeepsReportedClocksAndDistinguishesKnownStagesFromGaps() {
        val night = "2026-04-10T"
        val session = sleep(
            start = "${night}20:00:00Z",
            end = "${night}23:00:00Z",
            startOffset = ZoneOffset.ofHours(2),
            endOffset = ZoneOffset.ofHours(3),
            stages = listOf(
                SleepSessionRecord.STAGE_TYPE_AWAKE to 0,
                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED to 1,
                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED to 2,
                SleepSessionRecord.STAGE_TYPE_LIGHT to 3,
                SleepSessionRecord.STAGE_TYPE_DEEP to 4,
                SleepSessionRecord.STAGE_TYPE_REM to 5,
                SleepSessionRecord.STAGE_TYPE_SLEEPING to 6,
                SleepSessionRecord.STAGE_TYPE_UNKNOWN to 9
            ).map { (kind, minute) ->
                val start = Instant.parse("${night}20:00:00Z").plusSeconds(minute * 60L)
                SleepSessionRecord.Stage(start, start.plusSeconds(60), kind)
            }
        )

        assertEquals(
            RecordedSleep(
                "2026-04-10T22:00+02:00",
                "2026-04-11T02:00+03:00",
                listOf(
                    SleepPhase(0, 60, "awake"),
                    SleepPhase(60, 120, "awake"),
                    SleepPhase(120, 180, "awake"),
                    SleepPhase(180, 240, "light"),
                    SleepPhase(240, 300, "deep"),
                    SleepPhase(300, 360, "rem"),
                    SleepPhase(360, 420, "asleep"),
                    SleepPhase(540, 600, "unknown")
                )
            ),
            recordedSleep(session, london)
        )
    }

    private fun sleep(
        start: String,
        end: String,
        endOffset: ZoneOffset? = null,
        startOffset: ZoneOffset? = null,
        stages: List<SleepSessionRecord.Stage> = emptyList()
    ): SleepSessionRecord =
        SleepSessionRecord(
            startTime = Instant.parse(start),
            startZoneOffset = startOffset,
            endTime = Instant.parse(end),
            endZoneOffset = endOffset,
            metadata = Metadata.manualEntry(),
            stages = stages
        )
}
