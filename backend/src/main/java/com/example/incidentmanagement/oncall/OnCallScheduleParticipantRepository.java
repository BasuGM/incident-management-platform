package com.example.incidentmanagement.oncall;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface OnCallScheduleParticipantRepository extends JpaRepository<OnCallScheduleParticipant, UUID> {

    @EntityGraph(attributePaths = {"user"})
    List<OnCallScheduleParticipant> findBySchedule_IdOrderByPositionAsc(UUID scheduleId);

    long countBySchedule_Id(UUID scheduleId);

    @Modifying
    @Transactional
    void deleteBySchedule_Id(UUID scheduleId);
}
