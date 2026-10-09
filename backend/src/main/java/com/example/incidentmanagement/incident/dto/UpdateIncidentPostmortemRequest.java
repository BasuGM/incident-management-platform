package com.example.incidentmanagement.incident.dto;

import com.example.incidentmanagement.incident.PostmortemDraftUpdate;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public class UpdateIncidentPostmortemRequest {

    @Size(max = 200)
    private String title;

    @Size(max = 10000)
    private String summary;

    @Size(max = 10000)
    private String impact;

    @Size(max = 10000)
    private String rootCause;

    @Size(max = 10000)
    private String resolution;

    @Size(max = 10000)
    private String lessonsLearned;

    @Size(max = 10000)
    private String correctiveActions;

    private boolean titlePresent;
    private boolean summaryPresent;
    private boolean impactPresent;
    private boolean rootCausePresent;
    private boolean resolutionPresent;
    private boolean lessonsLearnedPresent;
    private boolean correctiveActionsPresent;

    public static UpdateIncidentPostmortemRequest parse(JsonNode node) {
        UpdateIncidentPostmortemRequest request = new UpdateIncidentPostmortemRequest();
        if (node == null || node.isNull()) {
            return request;
        }
        if (node.has("title")) {
            request.titlePresent = true;
            request.title = textOrNull(node.get("title"));
        }
        if (node.has("summary")) {
            request.summaryPresent = true;
            request.summary = textOrNull(node.get("summary"));
        }
        if (node.has("impact")) {
            request.impactPresent = true;
            request.impact = textOrNull(node.get("impact"));
        }
        if (node.has("rootCause")) {
            request.rootCausePresent = true;
            request.rootCause = textOrNull(node.get("rootCause"));
        }
        if (node.has("resolution")) {
            request.resolutionPresent = true;
            request.resolution = textOrNull(node.get("resolution"));
        }
        if (node.has("lessonsLearned")) {
            request.lessonsLearnedPresent = true;
            request.lessonsLearned = textOrNull(node.get("lessonsLearned"));
        }
        if (node.has("correctiveActions")) {
            request.correctiveActionsPresent = true;
            request.correctiveActions = textOrNull(node.get("correctiveActions"));
        }
        return request;
    }

    public PostmortemDraftUpdate toDraftUpdate() {
        PostmortemDraftUpdate.Builder builder = PostmortemDraftUpdate.builder();
        if (titlePresent) {
            builder.title(title);
        }
        if (summaryPresent) {
            builder.summary(summary);
        }
        if (impactPresent) {
            builder.impact(impact);
        }
        if (rootCausePresent) {
            builder.rootCause(rootCause);
        }
        if (resolutionPresent) {
            builder.resolution(resolution);
        }
        if (lessonsLearnedPresent) {
            builder.lessonsLearned(lessonsLearned);
        }
        if (correctiveActionsPresent) {
            builder.correctiveActions(correctiveActions);
        }
        return builder.build();
    }

    private static String textOrNull(JsonNode node) {
        return node.isNull() ? null : node.asText();
    }
}
