package com.arc.orderslambda.auth;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Validates that a Bearer JWT token carries the required OAuth2 scope claim.
 *
 * <p><b>Security note</b>: This class <em>parses</em> the JWT to extract scope claims but does
 * <em>not</em> perform cryptographic signature verification. Signature verification is
 * expected to be enforced by the API Gateway JWT Authorizer configured on the HTTP API route.
 * Any request reaching the Lambda has already had its signature verified by API Gateway.
 * This component provides a defence-in-depth scope check inside the Lambda boundary.
 *
 * <p>The {@code scope} claim may be either a space-separated string
 * (RFC 8693 / standard OAuth2) or a JSON array, depending on the IdP.
 * Both forms are handled.
 */
@Component
public class ScopeValidator {

    private static final Logger log = LoggerFactory.getLogger(ScopeValidator.class);

    /** The required scope for the orders search endpoint. */
    public static final String REQUIRED_SCOPE = "orders:read";

    /**
     * Validates that the supplied Authorization header contains a JWT with the
     * {@code orders:read} scope.
     *
     * @param authorizationHeader the raw {@code Authorization} header value
     *                            (expected format: {@code Bearer <token>})
     * @throws SecurityException if the header is absent, malformed, or missing the required scope
     */
    public void validate(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new SecurityException("Missing Authorization header");
        }

        String token = extractBearerToken(authorizationHeader);
        JWTClaimsSet claims = parseClaims(token);
        assertScope(claims);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String extractBearerToken(String header) {
        if (!header.startsWith("Bearer ")) {
            throw new SecurityException("Authorization header must use Bearer scheme");
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new SecurityException("Bearer token is empty");
        }
        return token;
    }

    private JWTClaimsSet parseClaims(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            log.warn("Failed to parse JWT: {}", e.getMessage());
            throw new SecurityException("Invalid JWT token");
        }
    }

    private void assertScope(JWTClaimsSet claims) {
        // Try string form first (space-delimited scopes per RFC 8693)
        Object scopeClaim = claims.getClaim("scope");
        if (scopeClaim instanceof String scopeString) {
            List<String> scopes = Arrays.asList(scopeString.split("\\s+"));
            if (scopes.contains(REQUIRED_SCOPE)) {
                return;
            }
        }

        // Try list/array form (some IdPs emit scope as a JSON array)
        if (scopeClaim instanceof List<?> scopeList) {
            boolean found = scopeList.stream()
                    .anyMatch(s -> REQUIRED_SCOPE.equals(String.valueOf(s)));
            if (found) {
                return;
            }
        }

        log.warn("JWT is missing required scope '{}'. Present scope claim: {}", REQUIRED_SCOPE, scopeClaim);
        throw new SecurityException("Insufficient scope: '" + REQUIRED_SCOPE + "' is required");
    }
}
