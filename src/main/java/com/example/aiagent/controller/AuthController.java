package com.example.aiagent.controller;

import com.example.aiagent.security.AuthRequest;
import com.example.aiagent.security.JwtUtil;
import com.example.aiagent.security.User;
import com.example.aiagent.security.UserRepository;
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
@CrossOrigin(origins = "*")
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

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already taken"));
        }

        User user = new User(
                UUID.randomUUID().toString(),
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                Set.of("ROLE_USER")
        );
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getUsername());
        log.info("User registered: {}", user.getUsername());
        return ResponseEntity.ok(Map.of(
                "message", "Registration successful",
                "token", token,
                "username", user.getUsername()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody AuthRequest request) {
        var userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }

        String token = jwtUtil.generateToken(user.getUsername());
        log.info("User logged in: {}", user.getUsername());
        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "token", token,
                "username", user.getUsername()
        ));
    }
}
