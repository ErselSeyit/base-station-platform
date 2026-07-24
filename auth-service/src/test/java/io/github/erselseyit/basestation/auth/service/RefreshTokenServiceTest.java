package io.github.erselseyit.basestation.auth.service;

import io.github.erselseyit.basestation.auth.model.RefreshToken;
import io.github.erselseyit.basestation.auth.model.User;
import io.github.erselseyit.basestation.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private SecurityAuditService auditService;

    @InjectMocks
    private RefreshTokenService service;

    private User user() {
        User u = new User();
        u.setUsername("alice");
        return u;
    }

    @Test
    void reuseOfRevokedTokenRevokesTheWholeFamily() {
        // Given a token that has already been revoked (i.e. already rotated),
        User user = user();
        RefreshToken revoked = new RefreshToken(user, 604800000L);
        revoked.revoke("Token rotation");
        when(refreshTokenRepository.findByToken("stolen")).thenReturn(Optional.of(revoked));

        // When it is replayed,
        Optional<RefreshToken> result = service.verifyRefreshToken("stolen");

        // Then it is rejected AND every token for that user is revoked (reuse
        // detection — RFC 6819 / OAuth 2 refresh-token reuse).
        assertTrue(result.isEmpty());
        verify(refreshTokenRepository).revokeAllByUser(eq(user), any(Instant.class), anyString());
    }

    @Test
    void validTokenIsReturnedAndDoesNotTriggerFamilyRevocation() {
        User user = user();
        RefreshToken valid = new RefreshToken(user, 604800000L); // not revoked, not expired
        when(refreshTokenRepository.findByToken("good")).thenReturn(Optional.of(valid));

        Optional<RefreshToken> result = service.verifyRefreshToken("good");

        assertTrue(result.isPresent());
        verify(refreshTokenRepository, never()).revokeAllByUser(any(), any(), anyString());
    }

    @Test
    void unknownTokenReturnsEmpty() {
        when(refreshTokenRepository.findByToken("nope")).thenReturn(Optional.empty());
        assertTrue(service.verifyRefreshToken("nope").isEmpty());
    }
}
