package com.example.aiagent.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<User> rowMapper = (rs, rowNum) -> {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setRoles(Set.of(rs.getString("roles").split(",")));
        return user;
    };

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public User save(User user) {
        jdbcTemplate.update(
                "INSERT INTO users (id, username, password, roles) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (username) DO UPDATE SET password = EXCLUDED.password, roles = EXCLUDED.roles",
                user.getId(), user.getUsername(), user.getPassword(),
                String.join(",", user.getRoles()));
        return user;
    }

    public Optional<User> findByUsername(String username) {
        return jdbcTemplate.query("SELECT * FROM users WHERE username = ?", rowMapper, username)
                .stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        return Boolean.TRUE.equals(
                jdbcTemplate.queryForObject("SELECT COUNT(*) > 0 FROM users WHERE username = ?", Boolean.class, username));
    }
}
