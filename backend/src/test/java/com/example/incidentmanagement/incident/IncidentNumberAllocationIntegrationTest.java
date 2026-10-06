package com.example.incidentmanagement.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.incidentmanagement.organization.Organization;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.support.DatabaseCleaner;
import com.example.incidentmanagement.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class IncidentNumberAllocationIntegrationTest extends IntegrationTestBase {

    private static final int CONCURRENT_ALLOCATIONS = 16;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private IncidentNumberAllocator incidentNumberAllocator;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanAll();
    }

    @Test
    void allocatesSequentialNumbersPerOrganization() {
        Organization organization = createOrganization();

        long first = incidentNumberAllocator.allocateNextIncidentNumber(organization.getId());
        long second = incidentNumberAllocator.allocateNextIncidentNumber(organization.getId());

        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
    }

    @Test
    void differentOrganizationsStartAtOne() {
        Organization orgA = createOrganization();
        Organization orgB = createOrganization();

        long numberA = incidentNumberAllocator.allocateNextIncidentNumber(orgA.getId());
        long numberB = incidentNumberAllocator.allocateNextIncidentNumber(orgB.getId());

        assertThat(numberA).isEqualTo(1L);
        assertThat(numberB).isEqualTo(1L);
    }

    @Test
    void concurrentAllocationsProduceUniqueNumbers() throws Exception {
        Organization organization = createOrganization();
        UUID organizationId = organization.getId();

        Set<Long> assigned = ConcurrentHashMap.newKeySet();
        List<Future<Long>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ALLOCATIONS);

        try {
            for (int i = 0; i < CONCURRENT_ALLOCATIONS; i++) {
                futures.add(executor.submit(() -> transactionTemplate.execute(
                        status -> incidentNumberAllocator.allocateNextIncidentNumber(organizationId))));
            }
            for (Future<Long> future : futures) {
                assigned.add(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(assigned).hasSize(CONCURRENT_ALLOCATIONS);
        assertThat(assigned).containsExactlyInAnyOrderElementsOf(
                java.util.stream.LongStream.rangeClosed(1, CONCURRENT_ALLOCATIONS).boxed().toList());
    }

    private Organization createOrganization() {
        Organization organization = new Organization();
        organization.setName("Org " + UUID.randomUUID());
        organization.setSlug("org-" + UUID.randomUUID().toString().substring(0, 8));
        return organizationRepository.save(organization);
    }
}
