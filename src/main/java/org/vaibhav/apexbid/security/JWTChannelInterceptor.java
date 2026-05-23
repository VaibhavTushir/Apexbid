package org.vaibhav.apexbid.security;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Slf4j
@Component
public class JWTChannelInterceptor implements ChannelInterceptor {
    private final JwtService jwtService;

    public JWTChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            // Attempt JWT authentication
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String jwt = authHeader.substring(7);
                    Claims extractedJwt = jwtService.extractAllClaims(jwt);

                    String email = extractedJwt.getSubject();
                    if (email != null) {
                        // Extract claims
                        Long id = extractedJwt.get("userId", Long.class);
                        String username = extractedJwt.get("username", String.class);
                        String role = extractedJwt.get("role", String.class);

                        // Build stateless principal
                        AuthenticatedUser principal = new AuthenticatedUser(id, email, username);
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                principal, null, Collections.singletonList(new SimpleGrantedAuthority(role))
                        );

                        // Bind identity to WebSocket session
                        accessor.setUser(authToken);
                    }
                } catch (Exception e) {
                    // Downgrade to anonymous guest on invalid token
                    log.warn("WebSocket JWT Auth failed (Connecting as Guest): {}", e.getMessage());
                }
            }
        } else if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();

            // Guard private channels against unauthenticated connections
            if (destination != null && destination.startsWith("/user/queue/") && accessor.getUser() == null) {
                throw new IllegalArgumentException("Unauthorized: Login required for private queues.");
            }
        }

        return message;
    }
}