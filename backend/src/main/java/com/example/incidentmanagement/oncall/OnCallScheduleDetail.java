package com.example.incidentmanagement.oncall;

import java.util.List;

public record OnCallScheduleDetail(OnCallScheduleSummary summary, List<OnCallScheduleParticipantView> participants) {}
