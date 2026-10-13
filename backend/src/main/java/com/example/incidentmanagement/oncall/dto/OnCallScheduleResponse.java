package com.example.incidentmanagement.oncall.dto;

import com.example.incidentmanagement.oncall.OnCallScheduleDetail;
import java.util.List;

public record OnCallScheduleResponse(
        OnCallScheduleSummaryResponse schedule, List<OnCallScheduleParticipantResponse> participants) {

    public static OnCallScheduleResponse from(OnCallScheduleDetail detail) {
        return new OnCallScheduleResponse(
                OnCallScheduleSummaryResponse.fromDetail(detail),
                detail.participants().stream()
                        .map(OnCallScheduleParticipantResponse::from)
                        .toList());
    }
}
