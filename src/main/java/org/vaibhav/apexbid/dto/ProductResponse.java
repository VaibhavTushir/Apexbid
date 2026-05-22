package org.vaibhav.apexbid.dto;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String imageUrl
) {
}
