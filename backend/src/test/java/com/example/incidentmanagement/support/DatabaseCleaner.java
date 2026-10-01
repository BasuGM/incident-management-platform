package com.example.incidentmanagement.support;

import com.example.incidentmanagement.auth.RefreshTokenRepository;
import com.example.incidentmanagement.organization.OrganizationMemberRepository;
import com.example.incidentmanagement.organization.OrganizationRepository;
import com.example.incidentmanagement.team.TeamMemberRepository;
import com.example.incidentmanagement.team.TeamRepository;
import com.example.incidentmanagement.user.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public DatabaseCleaner(
            TeamMemberRepository teamMemberRepository,
            TeamRepository teamRepository,
            OrganizationMemberRepository organizationMemberRepository,
            OrganizationRepository organizationRepository,
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository) {
        this.teamMemberRepository = teamMemberRepository;
        this.teamRepository = teamRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.organizationRepository = organizationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    public void cleanAll() {
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        organizationMemberRepository.deleteAll();
        organizationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }
}
