package com.example.incidentmanagement.oncall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OnCallRotationCalculatorTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final OnCallRotationCalculator calculator = new OnCallRotationCalculator();

    @Test
    void dailyRotationSelectsParticipantAcrossHandoffs() {
        OnCallRotationInput input = dailyInput("UTC", LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), List.of(A, B, C), 2);

        assertParticipantAt(input, Instant.parse("2026-01-01T08:59:59Z"), null);
        assertParticipantAt(input, Instant.parse("2026-01-01T09:00:00Z"), A);
        assertParticipantAt(input, Instant.parse("2026-01-02T08:59:59Z"), A);
        assertParticipantAt(input, Instant.parse("2026-01-02T09:00:00Z"), B);
        assertParticipantAt(input, Instant.parse("2026-01-03T09:00:00Z"), C);
        assertParticipantAt(input, Instant.parse("2026-01-04T09:00:00Z"), A);
    }

    @Test
    void weeklyRotationUsesConfiguredWeekdayOnOrAfterEffectiveFrom() {
        // 2026-01-06 is Tuesday; first Monday on or after is 2026-01-12.
        OnCallRotationInput input = weeklyInput(
                "UTC",
                LocalDate.of(2026, 1, 6),
                (short) 1,
                LocalTime.of(10, 0),
                List.of(A, B),
                1);

        assertThat(calculator.calculate(input, Instant.parse("2026-01-12T09:59:59Z")).currentShift())
                .isNull();
        assertParticipantAt(input, Instant.parse("2026-01-12T10:00:00Z"), A);
        assertParticipantAt(input, Instant.parse("2026-01-19T10:00:00Z"), B);
    }

    @Test
    void weeklyRotationStartsOnEffectiveDateWhenWeekdayMatches() {
        // 2026-01-05 is Monday.
        OnCallRotationInput input = weeklyInput(
                "UTC",
                LocalDate.of(2026, 1, 5),
                (short) 1,
                LocalTime.of(8, 0),
                List.of(A),
                0);

        assertThat(calculator.calculate(input, Instant.parse("2026-01-05T08:00:00Z")).nextHandoffAt())
                .isEqualTo(Instant.parse("2026-01-12T08:00:00Z"));
        assertParticipantAt(input, Instant.parse("2026-01-05T08:00:00Z"), A);
    }

    @Test
    void singleParticipantAlwaysOnCallAfterAnchor() {
        OnCallRotationInput input = dailyInput("UTC", LocalDate.of(2026, 6, 1), LocalTime.NOON, List.of(A), 0);

        assertParticipantAt(input, Instant.parse("2026-06-01T12:00:00Z"), A);
        assertParticipantAt(input, Instant.parse("2026-12-31T23:59:59Z"), A);
    }

    @Test
    void repeatedEvaluationAtSameInstantIsStable() {
        OnCallRotationInput input = dailyInput("UTC", LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), List.of(A, B), 1);
        Instant at = Instant.parse("2026-01-03T15:00:00Z");

        OnCallCoverageResult first = calculator.calculate(input, at);
        OnCallCoverageResult second = calculator.calculate(input, at);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void beforeEffectiveStartHasNoCurrentAndNextHandoffAtAnchor() {
        OnCallRotationInput input = dailyInput("UTC", LocalDate.of(2026, 2, 1), LocalTime.of(9, 0), List.of(A, B), 2);
        Instant anchor = Instant.parse("2026-02-01T09:00:00Z");

        OnCallCoverageResult result = calculator.calculate(input, Instant.parse("2026-02-01T08:59:59Z"));

        assertThat(result.currentShift()).isNull();
        assertThat(result.nextHandoffAt()).isEqualTo(anchor);
        assertThat(result.upcomingShifts()).hasSize(2);
        assertThat(result.upcomingShifts().getFirst().participantUserId()).isEqualTo(A);
        assertThat(result.upcomingShifts().getFirst().shiftStartsAt()).isEqualTo(anchor);
    }

    @Test
    void upcomingShiftsAfterCurrentAreOrderedAndContiguous() {
        OnCallRotationInput input = dailyInput("UTC", LocalDate.of(2026, 1, 1), LocalTime.of(0, 0), List.of(A, B), 3);
        OnCallCoverageResult result = calculator.calculate(input, Instant.parse("2026-01-02T00:00:00Z"));

        assertThat(result.currentShift().participantUserId()).isEqualTo(B);
        assertThat(result.upcomingShifts()).hasSize(3);
        assertThat(result.upcomingShifts().get(0).participantUserId()).isEqualTo(A);
        assertThat(result.upcomingShifts().get(0).shiftStartsAt()).isEqualTo(Instant.parse("2026-01-03T00:00:00Z"));
        assertThat(result.upcomingShifts().get(1).shiftStartsAt()).isEqualTo(Instant.parse("2026-01-04T00:00:00Z"));
        assertThat(result.upcomingShifts().get(2).shiftStartsAt()).isEqualTo(Instant.parse("2026-01-05T00:00:00Z"));
        assertThat(result.upcomingShifts().get(0).shiftEndsAt()).isEqualTo(result.upcomingShifts().get(1).shiftStartsAt());
    }

    @Test
    void nonUtcTimezoneUsesZoneRules() {
        OnCallRotationInput input = dailyInput(
                "Asia/Tokyo",
                LocalDate.of(2026, 3, 1),
                LocalTime.of(9, 0),
                List.of(A, B),
                0);

        // 09:00 JST = 00:00 UTC
        assertParticipantAt(input, Instant.parse("2026-03-01T00:00:00Z"), A);
        assertParticipantAt(input, Instant.parse("2026-03-02T00:00:00Z"), B);
    }

    @Test
    void springForwardGapShiftsHandoffForward() {
        // US spring forward 2025-03-09: 02:00 -> 03:00. 02:30 does not exist; resolves forward.
        OnCallRotationInput input = dailyInput(
                "America/New_York",
                LocalDate.of(2025, 3, 9),
                LocalTime.of(2, 30),
                List.of(A),
                0);

        var anchorZdt = OnCallRotationCalculator.resolveAnchorHandoff(
                input, java.time.ZoneId.of("America/New_York"));
        assertThat(anchorZdt.getHour()).isEqualTo(3);
        assertThat(anchorZdt.getMinute()).isEqualTo(30);

        OnCallCoverageResult before = calculator.calculate(input, Instant.parse("2025-03-09T06:29:59Z"));
        assertThat(before.currentShift()).isNull();

        OnCallCoverageResult at = calculator.calculate(input, Instant.parse("2025-03-09T07:30:00Z"));
        assertThat(at.currentShift()).isNotNull();
        assertThat(at.currentShift().participantUserId()).isEqualTo(A);
    }

    @Test
    void fallBackOverlapUsesEarlierOffsetByDefault() {
        // US fall back 2025-11-02: 01:30 can be EDT or EST; Java default is earlier (EDT).
        OnCallRotationInput input = dailyInput(
                "America/New_York",
                LocalDate.of(2025, 11, 2),
                LocalTime.of(1, 30),
                List.of(A),
                0);

        Instant anchor = OnCallRotationCalculator.resolveAnchorHandoff(
                        input, java.time.ZoneId.of("America/New_York"))
                .toInstant();
        OnCallCoverageResult at = calculator.calculate(input, anchor);

        assertThat(at.currentShift().shiftStartsAt()).isEqualTo(anchor);
        assertThat(anchor).isEqualTo(Instant.parse("2025-11-02T05:30:00Z")); // 01:30 EDT
    }

    @Test
    void rejectsEmptyParticipants() {
        OnCallRotationInput input =
                dailyInput("UTC", LocalDate.of(2026, 1, 1), LocalTime.NOON, List.of(), 0);

        assertThatThrownBy(() -> calculator.calculate(input, Instant.parse("2026-01-01T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsUnsupportedRotationLength() {
        OnCallRotationInput input = new OnCallRotationInput(
                "UTC", (short) 3, LocalTime.NOON, null, LocalDate.of(2026, 1, 1), List.of(A), 0);

        assertThatThrownBy(() -> calculator.calculate(input, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWeeklyWithoutHandoffDay() {
        OnCallRotationInput input = new OnCallRotationInput(
                "UTC", (short) 7, LocalTime.NOON, null, LocalDate.of(2026, 1, 1), List.of(A), 0);

        assertThatThrownBy(() -> calculator.calculate(input, Instant.EPOCH))
                .hasMessageContaining("handoffDayOfWeek");
    }

    @Test
    void rejectsDailyWithHandoffDay() {
        OnCallRotationInput input = new OnCallRotationInput(
                "UTC", (short) 1, LocalTime.NOON, (short) 1, LocalDate.of(2026, 1, 1), List.of(A), 0);

        assertThatThrownBy(() -> calculator.calculate(input, Instant.EPOCH))
                .hasMessageContaining("must be null");
    }

    @Test
    void rejectsInvalidTimezone() {
        OnCallRotationInput input = dailyInput("Not/AZone", LocalDate.of(2026, 1, 1), LocalTime.NOON, List.of(A), 0);

        assertThatThrownBy(() -> calculator.calculate(input, Instant.EPOCH))
                .hasMessageContaining("Invalid timezone");
    }

    @Test
    void rejectsNullQueriedAt() {
        OnCallRotationInput input = dailyInput("UTC", LocalDate.of(2026, 1, 1), LocalTime.NOON, List.of(A), 0);

        assertThatThrownBy(() -> calculator.calculate(input, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertParticipantAt(OnCallRotationInput input, Instant at, UUID expected) {
        OnCallCoverageResult result = calculator.calculate(input, at);
        if (expected == null) {
            assertThat(result.currentShift()).isNull();
        } else {
            assertThat(result.currentShift()).isNotNull();
            assertThat(result.currentShift().participantUserId()).isEqualTo(expected);
        }
    }

    private static OnCallRotationInput dailyInput(
            String zone,
            LocalDate effectiveFrom,
            LocalTime handoff,
            List<UUID> participants,
            int upcoming) {
        return new OnCallRotationInput(zone, (short) 1, handoff, null, effectiveFrom, participants, upcoming);
    }

    private static OnCallRotationInput weeklyInput(
            String zone,
            LocalDate effectiveFrom,
            short dow,
            LocalTime handoff,
            List<UUID> participants,
            int upcoming) {
        return new OnCallRotationInput(zone, (short) 7, handoff, dow, effectiveFrom, participants, upcoming);
    }

}
