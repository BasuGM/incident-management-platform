package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.incidentmanagement.common.exception.ApiClientException;
import com.example.incidentmanagement.common.exception.ConflictException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentListCriteriaParserTest {

    private static final UUID CALLER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void emptyParamsYieldEmptyCriteria() {
        IncidentListCriteria criteria =
                IncidentListCriteriaParser.parse(null, null, null, null, null, null, null, CALLER);
        assertThat(criteria.active()).isEmpty();
        assertThat(criteria.status()).isEmpty();
        assertThat(criteria.search()).isEmpty();
    }

    @Test
    void mineResolvesToCaller() {
        IncidentListCriteria criteria =
                IncidentListCriteriaParser.parse(null, null, null, null, null, "true", null, CALLER);
        assertThat(criteria.commanderId()).contains(CALLER);
    }

    @Test
    void commanderMeResolvesToCaller() {
        IncidentListCriteria criteria =
                IncidentListCriteriaParser.parse(null, null, null, null, "me", null, null, CALLER);
        assertThat(criteria.commanderId()).contains(CALLER);
    }

    @Test
    void mineAndCommanderIdConflict() {
        assertThatThrownBy(() -> IncidentListCriteriaParser.parse(
                        null, null, null, null, UUID.randomUUID().toString(), "true", null, CALLER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("mine or commanderId");
    }

    @Test
    void invalidServiceIdIsBadRequest() {
        assertThatThrownBy(() -> IncidentListCriteriaParser.parse(
                        null, null, null, "not-a-uuid", null, null, null, CALLER))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("serviceId");
    }

    @Test
    void searchWhitespaceIgnored() {
        IncidentListCriteria criteria =
                IncidentListCriteriaParser.parse(null, null, null, null, null, null, "   ", CALLER);
        assertThat(criteria.search()).isEmpty();
    }

    @Test
    void searchTooLongRejected() {
        String longSearch = "a".repeat(201);
        assertThatThrownBy(() -> IncidentListCriteriaParser.parse(
                        null, null, null, null, null, null, longSearch, CALLER))
                .isInstanceOf(ConflictException.class);
    }
}
