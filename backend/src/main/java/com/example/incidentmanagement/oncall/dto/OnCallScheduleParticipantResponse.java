package com.example.incidentmanagement.oncall.dto;

import com.example.incidentmanagement.oncall.OnCallScheduleParticipantView;
import java.util.UUID;

public record OnCallScheduleParticipantResponse(UUID userId, String displayName, int position) {

    public static OnCallScheduleParticipantResponse from(OnCallScheduleParticipantView view) {
        return new OnCallScheduleParticipantResponse(view.userId(), view.displayName(), view.position());
    }
}
