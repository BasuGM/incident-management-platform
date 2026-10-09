package com.example.incidentmanagement.incident;

/** Internal service-layer input for postmortem creation (REST DTOs are Step 3). */
public record CreatePostmortemParams(
        String title,
        String summary,
        String impact,
        String rootCause,
        String resolution,
        String lessonsLearned,
        String correctiveActions) {

    public static CreatePostmortemParams empty() {
        return new CreatePostmortemParams(null, null, null, null, null, null, null);
    }
}
