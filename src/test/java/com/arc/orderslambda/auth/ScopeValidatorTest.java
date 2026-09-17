package com.arc.orderslambda.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ScopeValidator} — covers the auth/scope branches (REQ-6).
 *
 * <p>Tokens are real HMAC-signed JWTs built with Nimbus so the production parse path
 * ({@code SignedJWT.parse}) is exercised. {@code ScopeValidator} does not verify the
 * signature (that is the edge authorizer's job), so any well-formed signed JWT parses;
 * we only assert on scope-claim handling and malformed-input rejection.
 *
 * <p>Follows Arrange–Act–Assert; deterministic (no clock/network).
 */
class ScopeValidatorTest {

    /** 256-bit secret — required minimum length for HS256. Test-only, not a real credential. */
    private static final byte[] TEST_SECRET = "0123456789abcdef0123456789abcdef".getBytes();

    private ScopeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ScopeValidator();
    }

    // ── Malformed / missing header branches ─────────────────────────────────────

    @Test
    @DisplayName("Null Authorization header is rejected")
    void validate_nullHeader_throws() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Missing Authorization header");
    }

    @Test
    @DisplayName("Blank Authorization header is rejected")
    void validate_blankHeader_throws() {
        assertThatThrownBy(() -> validator.validate("   "))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Missing Authorization header");
    }

    @Test
    @DisplayName("Non-Bearer scheme is rejected")
    void validate_nonBearerScheme_throws() {
        assertThatThrownBy(() -> validator.validate("Basic abc123"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Bearer scheme");
    }

    @Test
    @DisplayName("Empty bearer token is rejected")
    void validate_emptyBearerToken_throws() {
        assertThatThrownBy(() -> validator.validate("Bearer    "))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Bearer token is empty");
    }

    @Test
    @DisplayName("Unparseable token is rejected as invalid JWT")
    void validate_unparseableToken_throws() {
        assertThatThrownBy(() -> validator.validate("Bearer not-a-jwt"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid JWT token");
    }

    // ── Scope-claim branches ────────────────────────────────────────────────────

    @Test
    @DisplayName("Space-delimited scope string containing orders:read is accepted")
    void validate_stringScopeWithRequired_passes() throws Exception {
        String token = signedToken(new JWTClaimsSet.Builder()
                .claim("scope", "profile orders:read orders:write")
                .build());

        assertThatCode(() -> validator.validate("Bearer " + token))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JSON-array scope containing orders:read is accepted")
    void validate_arrayScopeWithRequired_passes() throws Exception {
        String token = signedToken(new JWTClaimsSet.Builder()
                .claim("scope", List.of("profile", "orders:read"))
                .build());

        assertThatCode(() -> validator.validate("Bearer " + token))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("String scope without orders:read is rejected as insufficient scope")
    void validate_stringScopeMissingRequired_throws() throws Exception {
        String token = signedToken(new JWTClaimsSet.Builder()
                .claim("scope", "profile orders:write")
                .build());

        assertThatThrownBy(() -> validator.validate("Bearer " + token))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Insufficient scope");
    }

    @Test
    @DisplayName("Array scope without orders:read is rejected as insufficient scope")
    void validate_arrayScopeMissingRequired_throws() throws Exception {
        String token = signedToken(new JWTClaimsSet.Builder()
                .claim("scope", List.of("profile", "orders:write"))
                .build());

        assertThatThrownBy(() -> validator.validate("Bearer " + token))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Insufficient scope");
    }

    @Test
    @DisplayName("Absent scope claim is rejected as insufficient scope")
    void validate_noScopeClaim_throws() throws Exception {
        String token = signedToken(new JWTClaimsSet.Builder()
                .subject("user-1")
                .build());

        assertThatThrownBy(() -> validator.validate("Bearer " + token))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Insufficient scope");
    }

    // ── Helper: build a real HMAC-signed JWT ────────────────────────────────────

    private static String signedToken(JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(TEST_SECRET));
        return jwt.serialize();
    }
}
