package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.CreatePostmortemParams;
import jakarta.validation.constraints.Size;

public record CreateIncidentPostmortemRequest(
        @Size(max = 200) String title,
        @Size(max = 10000) String summary,
        @Size(max = 10000) String impact,
        @Size(max = 10000) String rootCause,
        @Size(max = 10000) String resolution,
        @Size(max = 10000) String lessonsLearned,
        @Size(max = 10000) String correctiveActions) {

    public CreatePostmortemParams toParams() {
        return new CreatePostmortemParams(
                title, summary, impact, rootCause, resolution, lessonsLearned, correctiveActions);
    }
}
