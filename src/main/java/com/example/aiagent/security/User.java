package com.example.aiagent.security;

import jakarta.validation.constraints.Size;

import java.util.Set;

public class User {

    private String id;
    private String username;
    @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    private String password;
    private Set<String> roles;

    public User() {}

    public User(String id, String username, String password, Set<String> roles) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.roles = roles;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }
}
