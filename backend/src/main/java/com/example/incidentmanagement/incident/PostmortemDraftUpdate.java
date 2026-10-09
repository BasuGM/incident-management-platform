package com.example.incidentmanagement.incident;

/**
 * Partial update for draft postmortem content. Fields are omitted unless explicitly set on the builder.
 */
public final class PostmortemDraftUpdate {

    private String title;
    private boolean titleSet;

    private String summary;
    private boolean summarySet;

    private String impact;
    private boolean impactSet;

    private String rootCause;
    private boolean rootCauseSet;

    private String resolution;
    private boolean resolutionSet;

    private String lessonsLearned;
    private boolean lessonsLearnedSet;

    private String correctiveActions;
    private boolean correctiveActionsSet;

    private PostmortemDraftUpdate() {}

    public static Builder builder() {
        return new Builder();
    }

    public boolean isTitleSet() {
        return titleSet;
    }

    public String getTitle() {
        return title;
    }

    public boolean isSummarySet() {
        return summarySet;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isImpactSet() {
        return impactSet;
    }

    public String getImpact() {
        return impact;
    }

    public boolean isRootCauseSet() {
        return rootCauseSet;
    }

    public String getRootCause() {
        return rootCause;
    }

    public boolean isResolutionSet() {
        return resolutionSet;
    }

    public String getResolution() {
        return resolution;
    }

    public boolean isLessonsLearnedSet() {
        return lessonsLearnedSet;
    }

    public String getLessonsLearned() {
        return lessonsLearned;
    }

    public boolean isCorrectiveActionsSet() {
        return correctiveActionsSet;
    }

    public String getCorrectiveActions() {
        return correctiveActions;
    }

    public static final class Builder {

        private final PostmortemDraftUpdate update = new PostmortemDraftUpdate();

        public Builder title(String title) {
            update.title = title;
            update.titleSet = true;
            return this;
        }

        public Builder summary(String summary) {
            update.summary = summary;
            update.summarySet = true;
            return this;
        }

        public Builder impact(String impact) {
            update.impact = impact;
            update.impactSet = true;
            return this;
        }

        public Builder rootCause(String rootCause) {
            update.rootCause = rootCause;
            update.rootCauseSet = true;
            return this;
        }

        public Builder resolution(String resolution) {
            update.resolution = resolution;
            update.resolutionSet = true;
            return this;
        }

        public Builder lessonsLearned(String lessonsLearned) {
            update.lessonsLearned = lessonsLearned;
            update.lessonsLearnedSet = true;
            return this;
        }

        public Builder correctiveActions(String correctiveActions) {
            update.correctiveActions = correctiveActions;
            update.correctiveActionsSet = true;
            return this;
        }

        public PostmortemDraftUpdate build() {
            return update;
        }
    }
}
