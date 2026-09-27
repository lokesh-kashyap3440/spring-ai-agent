package com.example.aiagent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // Explicitly check if the token has expired before proceeding with other validation
        Date expiration = jwtUtil.extractExpiration(token);
        if (expiration != null && expiration.before(new Date())) {
            log.warn("Expired JWT token from {}", request.getRemoteAddr());
            filterChain.doFilter(request, response);
            return;
        }

        if (!jwtUtil.validateToken(token)) {
            log.warn("Invalid JWT token from {}", request.getRemoteAddr());
            filterChain.doFilter(request, response);
            return;
        }

        // Token-to-client binding: check that the token's clientIp matches the request's remote address
        String tokenClientIp = jwtUtil.extractClientIp(token);
        if (!tokenClientIp.isEmpty()) {
            String requestIp = resolveClientIp(request);
            if (!tokenClientIp.equals(requestIp)) {
                log.warn("Client IP mismatch for token subject '{}': token claims '{}' but request is from '{}'",
                        jwtUtil.extractUsername(token), tokenClientIp, requestIp);
            }
        }

        String username = jwtUtil.extractUsername(token);
        if (username == null || username.isBlank()) {
            log.warn("JWT token missing subject from {}", request.getRemoteAddr());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            var auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            log.warn("User lookup failed for token subject '{}': {}", username, e.getMessage());
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolves the client IP from the request, checking the X-Forwarded-For header
     * first (in case of reverse proxy), falling back to {@link HttpServletRequest#getRemoteAddr()}.
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "";
    }
}
