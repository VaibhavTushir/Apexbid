package org.vaibhav.apexbid.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vaibhav.apexbid.security.AuthenticatedUser;

@RestController
@RequestMapping("/demo")
public class DemoController {
    @Value("${NODE_ID:local-node}")
    private String nodeId;

    @GetMapping("/secret")
    public ResponseEntity<String> getSecretData(@AuthenticationPrincipal AuthenticatedUser user) {
        // If we reach here, the token was valid!
        return ResponseEntity
                .ok("Welcome to the VIP lounge, "
                        + user.username() + "! Your ID is "
                        + user.id() + " & the response is from " + nodeId);
    }
}
