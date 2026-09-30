package com.example.incidentmanagement.auth;

import com.example.incidentmanagement.common.exception.InvalidRefreshTokenException;
import com.example.incidentmanagement.config.JwtProperties;
import com.example.incidentmanagement.user.User;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            TokenHasher tokenHasher,
            JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public IssuedRefreshToken issue(User user) {
        String rawToken = generateRawToken();
        RefreshToken entity = persistToken(user, rawToken);
        return new IssuedRefreshToken(rawToken, entity);
    }

    @Transactional
    public RotationResult rotateToken(String rawToken) {
        RefreshToken existing = findActiveToken(rawToken);
        existing.setRevokedAt(Instant.now());
        String newRaw = generateRawToken();
        RefreshToken replacement = persistToken(existing.getUser(), newRaw);
        existing.setReplacedBy(replacement);
        refreshTokenRepository.save(existing);
        return new RotationResult(newRaw, existing.getUser());
    }

    @Transactional
    public void revoke(String rawToken) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        }
    }

    private RefreshToken findActiveToken(String rawToken) {
        RefreshToken token = refreshTokenRepository
                .findByTokenHash(tokenHasher.hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!token.isActive()) {
            throw new InvalidRefreshTokenException();
        }
        return token;
    }

    private RefreshToken persistToken(User user, String rawToken) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(tokenHasher.hash(rawToken));
        token.setExpiresAt(
                Instant.now().plusSeconds(jwtProperties.refreshTokenExpirationDays() * 24 * 60 * 60));
        return refreshTokenRepository.save(token);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssuedRefreshToken(String rawToken, RefreshToken entity) {}

    public record RotationResult(String rawToken, User user) {}
}
