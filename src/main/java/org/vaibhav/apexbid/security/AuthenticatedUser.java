package org.vaibhav.apexbid.security;

public record AuthenticatedUser(Long id, String email, String username) {
}
