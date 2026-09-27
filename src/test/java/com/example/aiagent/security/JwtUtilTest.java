package com.example.aiagent.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "my-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-32bytes!";
    private static final long EXPIRATION_MS = 3600000;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    }

    @Test
    void testGenerateAndExtractUsername() {
        String token = jwtUtil.generateToken("testuser");
        assertNotNull(token);
        assertEquals("testuser", jwtUtil.extractUsername(token));
    }

    @Test
    void testValidateTokenReturnsTrue() {
        String token = jwtUtil.generateToken("testuser");
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void testValidateTokenReturnsFalseForTampered() {
        String token = jwtUtil.generateToken("testuser");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    void testValidateTokenReturnsFalseForGarbage() {
        assertFalse(jwtUtil.validateToken("not-a-jwt-token"));
    }

    @Test
    void testExtractUsernameFromDifferentUsers() {
        String token1 = jwtUtil.generateToken("alice");
        String token2 = jwtUtil.generateToken("bob");
        assertEquals("alice", jwtUtil.extractUsername(token1));
        assertEquals("bob", jwtUtil.extractUsername(token2));
    }
}
