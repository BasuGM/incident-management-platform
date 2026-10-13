package com.example.incidentmanagement.support;

import com.example.incidentmanagement.auth.RefreshTokenRepository;
import com.example.incidentmanagement.organization.OrganizationMemberRepository;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.incident.IncidentCommentRepository;
import com.example.incidentmanagement.incident.IncidentEventRepository;
import com.example.incidentmanagement.incident.IncidentPostmortemRepository;
import com.example.incidentmanagement.incident.IncidentRepository;
import com.example.incidentmanagement.incident.OrganizationIncidentCounterRepository;
import com.example.incidentmanagement.oncall.OnCallScheduleParticipantRepository;
import com.example.incidentmanagement.oncall.OnCallScheduleRepository;
import com.example.incidentmanagement.service.ServiceRepository;
import com.example.incidentmanagement.team.TeamMemberRepository;
import com.example.incidentmanagement.team.TeamRepository;
import com.example.incidentmanagement.user.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

    private final IncidentCommentRepository incidentCommentRepository;
    private final IncidentPostmortemRepository incidentPostmortemRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentRepository incidentRepository;
    private final OrganizationIncidentCounterRepository organizationIncidentCounterRepository;
    private final OnCallScheduleParticipantRepository onCallScheduleParticipantRepository;
    private final OnCallScheduleRepository onCallScheduleRepository;
    private final ServiceRepository serviceRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public DatabaseCleaner(
            IncidentCommentRepository incidentCommentRepository,
            IncidentPostmortemRepository incidentPostmortemRepository,
            IncidentEventRepository incidentEventRepository,
            IncidentRepository incidentRepository,
            OrganizationIncidentCounterRepository organizationIncidentCounterRepository,
            OnCallScheduleParticipantRepository onCallScheduleParticipantRepository,
            OnCallScheduleRepository onCallScheduleRepository,
            ServiceRepository serviceRepository,
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            OrganizationMemberRepository organizationMemberRepository,
            OrganizationRepository organizationRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository) {
        this.incidentCommentRepository = incidentCommentRepository;
        this.incidentPostmortemRepository = incidentPostmortemRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.incidentRepository = incidentRepository;
        this.organizationIncidentCounterRepository = organizationIncidentCounterRepository;
        this.onCallScheduleParticipantRepository = onCallScheduleParticipantRepository;
        this.onCallScheduleRepository = onCallScheduleRepository;
        this.serviceRepository = serviceRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.organizationRepository = organizationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    public void cleanAll() {
        incidentPostmortemRepository.deleteAll();
        incidentCommentRepository.deleteAll();
        incidentEventRepository.deleteAll();
        incidentRepository.deleteAll();
        organizationIncidentCounterRepository.deleteAll();
        onCallScheduleParticipantRepository.deleteAll();
        onCallScheduleRepository.deleteAll();
        serviceRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        organizationMemberRepository.deleteAll();
        organizationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }
}
