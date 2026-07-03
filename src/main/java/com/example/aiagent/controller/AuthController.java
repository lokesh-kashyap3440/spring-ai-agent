package com.example.aiagent.controller;

import com.example.aiagent.security.AuthRequest;
import com.example.aiagent.security.JwtUtil;
import com.example.aiagent.security.User;
import com.example.aiagent.security.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody AuthRequest request,
                                                      HttpServletRequest servletRequest) {
        var userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        String clientIp = resolveClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        String token = jwtUtil.generateToken(user.getUsername(), clientIp, userAgent);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
        log.info("User logged in: {} from {}", user.getUsername(), clientIp);
        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "token", token,
                "refreshToken", refreshToken,
                "username", user.getUsername()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody AuthRequest request,
                                                         HttpServletRequest servletRequest) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already taken"));
        }

        String passwordError = validatePassword(request.getPassword());
        if (passwordError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", passwordError));
        }

        User user = new User(
                UUID.randomUUID().toString(),
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                Set.of("ROLE_USER")
        );
        userRepository.save(user);

        String clientIp = resolveClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        String token = jwtUtil.generateToken(user.getUsername(), clientIp, userAgent);
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());
        log.info("User registered: {} from {}", user.getUsername(), clientIp);
        return ResponseEntity.ok(Map.of(
                "message", "Registration successful",
                "token", token,
                "refreshToken", refreshToken,
                "username", user.getUsername()
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, String> request,
                                                        HttpServletRequest servletRequest) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Refresh token is required"));
        }

        if (!jwtUtil.validateToken(refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired refresh token"));
        }

        String username = jwtUtil.extractUsername(refreshToken);
        String clientIp = resolveClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        String newAccessToken = jwtUtil.generateToken(username, clientIp, userAgent);
        String newRefreshToken = jwtUtil.generateRefreshToken(username);

        log.info("Token refreshed for user: {} from {}", username, clientIp);
        return ResponseEntity.ok(Map.of(
                "message", "Token refreshed successfully",
                "token", newAccessToken,
                "refreshToken", newRefreshToken,
                "username", username
        ));
    }

    /**
     * Validates password complexity requirements.
     *
     * @param password the password to validate
     * @return null if valid, or an error message describing the requirements
     */
    private String validatePassword(String password) {
        if (password == null || password.length() < 8) {
            return "Password must be at least 8 characters long";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter";
        }
        if (!password.matches(".*\\d.*")) {
            return "Password must contain at least one digit";
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            return "Password must contain at least one special character";
        }
        return null;
    }

    /**
     * Resolves the client IP from the request, checking the X-Forwarded-For header
     * first (in case of reverse proxy), falling back to {@link HttpServletRequest#getRemoteAddr()}.
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a comma-separated list; take the first (original client) IP
            return forwarded.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "";
    }
}
