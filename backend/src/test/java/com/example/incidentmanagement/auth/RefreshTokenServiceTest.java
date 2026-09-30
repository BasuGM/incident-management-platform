package com.example.incidentmanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.incidentmanagement.common.exception.InvalidRefreshTokenException;
import com.example.incidentmanagement.config.JwtProperties;
import com.example.incidentmanagement.user.User;
import com.example.incidentmanagement.user.UserRole;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private TokenHasher tokenHasher;

    @BeforeEach
    void setUp() {
        tokenHasher = new TokenHasher();
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository, tokenHasher, new JwtProperties("secret-min-32-chars-long!!!!!!!", 900, 7));
    }

    @Test
    void rotatesRefreshTokenAndRevokesPrevious() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(UserRole.ENGINEER);

        String raw = "raw-refresh-token-value";
        RefreshToken existing = new RefreshToken();
        existing.setUser(user);
        existing.setTokenHash(tokenHasher.hash(raw));
        existing.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(tokenHasher.hash(raw))).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.RotationResult result = refreshTokenService.rotateToken(raw);

        assertThat(result.user()).isEqualTo(user);
        assertThat(result.rawToken()).isNotBlank().isNotEqualTo(raw);
        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(tokenHasher.hash(result.rawToken())).isNotEqualTo(existing.getTokenHash());
    }

    @Test
    void rejectsUnknownRefreshToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> refreshTokenService.revoke("missing"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
