package com.example.incidentmanagement.oncall;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure rotation engine for daily and weekly on-call schedules. Uses {@link ZoneId} rules for DST; does not
 * call system clocks.
 */
public final class OnCallRotationCalculator {

    public OnCallCoverageResult calculate(OnCallRotationInput input, Instant queriedAt) {
        if (queriedAt == null) {
            throw new IllegalArgumentException("queriedAt is required");
        }
        validateRotationLength(input.rotationLengthDays());
        if (input.rotationLengthDays() == 7 && input.handoffDayOfWeek() == null) {
            throw new IllegalArgumentException("handoffDayOfWeek is required for weekly rotations");
        }
        if (input.rotationLengthDays() == 1 && input.handoffDayOfWeek() != null) {
            throw new IllegalArgumentException("handoffDayOfWeek must be null for daily rotations");
        }
        if (input.participantUserIdsInOrder().isEmpty()) {
            throw new IllegalArgumentException("participantUserIdsInOrder must not be empty");
        }

        ZoneId zone = parseZone(input.timezoneId());
        ZonedDateTime anchor = resolveAnchorHandoff(input, zone);
        Instant anchorInstant = anchor.toInstant();

        if (queriedAt.isBefore(anchorInstant)) {
            List<OnCallRotationShift> upcoming = buildUpcomingShifts(
                    anchor, input.rotationLengthDays(), input.participantUserIdsInOrder(), input.upcomingShiftCount());
            return new OnCallCoverageResult(queriedAt, null, anchorInstant, upcoming);
        }

        int rotationIndex = rotationIndexAt(anchor, input.rotationLengthDays(), queriedAt);
        ZonedDateTime shiftStart = anchor.plusDays((long) rotationIndex * input.rotationLengthDays());
        ZonedDateTime shiftEnd = shiftStart.plusDays(input.rotationLengthDays());
        UUID participant = input.participantUserIdsInOrder()
                .get(rotationIndex % input.participantUserIdsInOrder().size());

        OnCallRotationShift current =
                new OnCallRotationShift(participant, shiftStart.toInstant(), shiftEnd.toInstant());
        Instant nextHandoff = shiftEnd.toInstant();

        ZonedDateTime upcomingStart = shiftEnd;
        List<OnCallRotationShift> upcoming = buildUpcomingShifts(
                upcomingStart,
                input.rotationLengthDays(),
                input.participantUserIdsInOrder(),
                input.upcomingShiftCount(),
                rotationIndex + 1);

        return new OnCallCoverageResult(queriedAt, current, nextHandoff, upcoming);
    }

    static ZonedDateTime resolveAnchorHandoff(OnCallRotationInput input, ZoneId zone) {
        LocalDate handoffDate = input.effectiveFrom();
        if (input.rotationLengthDays() == 7) {
            int targetDow = input.handoffDayOfWeek();
            if (targetDow < 1 || targetDow > 7) {
                throw new IllegalArgumentException("handoffDayOfWeek must be between 1 and 7");
            }
            while (handoffDate.getDayOfWeek().getValue() != targetDow) {
                handoffDate = handoffDate.plusDays(1);
            }
        }
        return localHandoffAt(handoffDate, input.handoffLocalTime(), zone);
    }

    /**
     * Resolves a local handoff in the schedule zone. Nonexistent local times (DST gap) shift forward per
     * {@link ZonedDateTime#of(LocalDate, java.time.LocalTime, ZoneId)}. Ambiguous times (DST overlap) use
     * the earlier offset (Java default).
     */
    static ZonedDateTime localHandoffAt(LocalDate date, java.time.LocalTime time, ZoneId zone) {
        try {
            return ZonedDateTime.of(date, time, zone);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid handoff local time in timezone " + zone.getId(), ex);
        }
    }

    private static int rotationIndexAt(ZonedDateTime anchor, short rotationLengthDays, Instant queriedAt) {
        int index = 0;
        ZonedDateTime shiftEnd = anchor.plusDays(rotationLengthDays);
        while (!queriedAt.isBefore(shiftEnd.toInstant())) {
            index++;
            shiftEnd = anchor.plusDays((long) (index + 1) * rotationLengthDays);
        }
        return index;
    }

    private static List<OnCallRotationShift> buildUpcomingShifts(
            ZonedDateTime firstShiftStart,
            short rotationLengthDays,
            List<UUID> participants,
            int count) {
        return buildUpcomingShifts(firstShiftStart, rotationLengthDays, participants, count, 0);
    }

    private static List<OnCallRotationShift> buildUpcomingShifts(
            ZonedDateTime firstShiftStart,
            short rotationLengthDays,
            List<UUID> participants,
            int count,
            int firstRotationIndex) {
        List<OnCallRotationShift> shifts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int rotationIndex = firstRotationIndex + i;
            ZonedDateTime start = firstShiftStart.plusDays((long) i * rotationLengthDays);
            ZonedDateTime end = start.plusDays(rotationLengthDays);
            UUID participant = participants.get(Math.floorMod(rotationIndex, participants.size()));
            shifts.add(new OnCallRotationShift(participant, start.toInstant(), end.toInstant()));
        }
        return shifts;
    }

    private static void validateRotationLength(short rotationLengthDays) {
        if (rotationLengthDays != 1 && rotationLengthDays != 7) {
            throw new IllegalArgumentException("rotationLengthDays must be 1 or 7");
        }
    }

    private static ZoneId parseZone(String timezoneId) {
        try {
            return ZoneId.of(timezoneId);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid timezone: " + timezoneId, ex);
        }
    }

    /** Maps schedule entity fields without loading participants from persistence. */
    public static OnCallRotationInput inputFromSchedule(
            OnCallSchedule schedule, List<UUID> participantUserIdsInOrder, int upcomingShiftCount) {
        return new OnCallRotationInput(
                schedule.getTimezone(),
                schedule.getRotationLengthDays(),
                schedule.getHandoffLocalTime(),
                schedule.getHandoffDayOfWeek(),
                schedule.getEffectiveFrom(),
                participantUserIdsInOrder,
                upcomingShiftCount);
    }
}
