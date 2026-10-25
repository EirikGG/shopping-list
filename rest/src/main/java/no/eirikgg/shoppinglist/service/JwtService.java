package no.eirikgg.shoppinglist.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// The login token: a JWT signed with HS256, holding the person (sub) and the
// session (sid). The signature proves the backend issued it, so a session id
// alone, e.g. from a log line, is not enough to log in.
//
// The session is still looked up in the database on every request, so logging
// out and server-side expiry keep working, which a JWT alone cannot do.
public class JwtService {

    private static final String ISSUER = "shopping-list";
    private static final String CLAIM_SESSION_ID = "sid";
    // HS256 needs a key at least as long as its 256-bit output.
    static final int MIN_SECRET_BYTES = 32;

    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public record Claims(int personId, UUID sessionId) {}

    public JwtService(String secret) {
        if (null == secret || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT_SECRET must be at least %d bytes. Generate one with: openssl rand -base64 48"
                            .formatted(MIN_SECRET_BYTES)
            );
        }
        this.algorithm = Algorithm.HMAC256(secret);
        // Only HS256 tokens from this issuer are accepted, so a token cannot
        // pick a weaker algorithm (such as "none") for itself.
        this.verifier = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withClaimPresence("sub")
                .withClaimPresence(CLAIM_SESSION_ID)
                .build();
    }

    public String createToken(int personId, UUID sessionId, Instant expiresAt) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(String.valueOf(personId))
                .withClaim(CLAIM_SESSION_ID, sessionId.toString())
                .withIssuedAt(Instant.now())
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    // Empty if the token is malformed, forged, from elsewhere or expired.
    public Optional<Claims> verify(String token) {
        try {
            DecodedJWT jwt = verifier.verify(token);
            return Optional.of(new Claims(
                    Integer.parseInt(jwt.getSubject()),
                    UUID.fromString(jwt.getClaim(CLAIM_SESSION_ID).asString())
            ));
        }
        catch (JWTVerificationException | IllegalArgumentException e) {
            // IllegalArgumentException: a sub or sid that is not a number or UUID.
            return Optional.empty();
        }
    }
}
