package com.example.aiagent.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final long REFRESH_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username) {
        return generateToken(username, "", "");
    }

    /**
     * Generates a JWT access token with client IP and user agent claims for
     * token-to-client binding. The user agent is stored as a SHA-256 hash
     * (hex-encoded) rather than the raw value to limit token size.
     *
     * @param username the subject
     * @param clientIp the client IP address (may be empty)
     * @param userAgent the User-Agent header value (may be empty)
     * @return a signed JWT string
     */
    public String generateToken(String username, String clientIp, String userAgent) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .claim("clientIp", clientIp)
                .claim("userAgentHash", hashUserAgent(userAgent))
                .signWith(key)
                .compact();
    }

    /**
     * Returns a hex-encoded SHA-256 hash of the user agent string.
     * Returns empty string if the input is null or blank.
     */
    private static String hashUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "";
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(userAgent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    /**
     * Generates a refresh token with 7-day expiry.
     * Refresh tokens use the same signing key and are validated the same way
     * as access tokens, but carry a longer expiration.
     */
    public String generateRefreshToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + REFRESH_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Extracts the expiration date from a JWT token.
     * Returns null if the token is invalid or cannot be parsed.
     */
    public Date extractExpiration(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration();
        } catch (JwtException e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Extracts the clientIp claim from a JWT token.
     * Returns empty string if the claim is not present or the token is invalid.
     */
    public String extractClientIp(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String ip = claims.get("clientIp", String.class);
            return ip != null ? ip : "";
        } catch (JwtException e) {
            return "";
        }
    }

    /**
     * Extracts the userAgentHash claim from a JWT token.
     * Returns empty string if the claim is not present or the token is invalid.
     */
    public String extractUserAgentHash(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String hash = claims.get("userAgentHash", String.class);
            return hash != null ? hash : "";
        } catch (JwtException e) {
            return "";
        }
    }
}
