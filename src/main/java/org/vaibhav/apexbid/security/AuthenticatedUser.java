package org.vaibhav.apexbid.security;

import java.security.Principal;

public record AuthenticatedUser(Long id, String email, String username) implements Principal {
    @Override
    public String getName() {
        return String.valueOf(id); // match the userId used in convertAndSendToUser in RedisMessageSubscriber
    }
}
