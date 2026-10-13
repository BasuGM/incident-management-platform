package com.example.incidentmanagement.oncall;

import com.example.incidentmanagement.user.User;
import java.util.UUID;

public record OnCallScheduleParticipantView(UUID userId, String displayName, int position) {

    public static OnCallScheduleParticipantView from(OnCallScheduleParticipant participant) {
        User user = participant.getUser();
        return new OnCallScheduleParticipantView(
                user.getId(), displayName(user), participant.getPosition());
    }

    private static String displayName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}
