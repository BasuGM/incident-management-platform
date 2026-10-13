package com.example.incidentmanagement.oncall;

import java.time.Instant;
import java.util.UUID;

/** One primary on-call coverage window for a single participant. */
public record OnCallRotationShift(UUID participantUserId, Instant shiftStartsAt, Instant shiftEndsAt) {}
