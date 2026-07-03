package com.example.aiagent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60_000;

    private final ConcurrentHashMap<String, RateLimitEntry> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Only rate-limit auth endpoints
        if (!path.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        long now = System.currentTimeMillis();

        RateLimitEntry entry = attempts.get(ip);
        if (entry == null || (now - entry.windowStart()) > WINDOW_MS) {
            attempts.put(ip, new RateLimitEntry(new AtomicInteger(1), now));
            filterChain.doFilter(request, response);
            return;
        }

        int count = entry.count().incrementAndGet();
        if (count > MAX_ATTEMPTS) {
            log.warn("Rate limit exceeded for IP: {} ({} attempts in last minute)", ip, count);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Periodic cleanup of stale rate-limit entries. Runs every 60 seconds.
     * Evicts entries whose time window has expired.
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        int before = attempts.size();
        attempts.entrySet().removeIf(e -> (now - e.getValue().windowStart()) > WINDOW_MS);
        int removed = before - attempts.size();
        if (removed > 0) {
            log.debug("Rate limit cleanup: removed {} stale entries, {} remaining", removed, attempts.size());
        }
    }

    private record RateLimitEntry(AtomicInteger count, long windowStart) {}
}
