package org.vaibhav.apexbid.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.vaibhav.apexbid.dto.ProductResponse;
import org.vaibhav.apexbid.entity.Product;
import org.vaibhav.apexbid.repository.ProductRepository;
import org.vaibhav.apexbid.security.AuthenticatedUser;
import org.vaibhav.apexbid.security.SecretEncryptionUtil;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final SecretEncryptionUtil secretEncryptionUtil;

    public ProductController(ProductRepository productRepository, SecretEncryptionUtil secretEncryptionUtil) {
        this.productRepository = productRepository;
        this.secretEncryptionUtil = secretEncryptionUtil;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Map to public DTO
        ProductResponse response = new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl()
        );

        // Tell Nginx (and the user's browser) to cache this for 1 hour
        CacheControl cacheControl = CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic();

        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .body(response);
    }

    @GetMapping("/{id}/secret-code")
    public ResponseEntity<String> getSecretCode(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Long userId = currentUser.id();

        boolean isSeller = product.getSeller().getId().equals(userId);
        boolean isBuyer = product.getBuyer() != null && product.getBuyer().getId().equals(userId);

        if (!isSeller && !isBuyer) {
            // FIX 1: Explicitly trigger a 403 Forbidden instead of a 500 Internal Server Error
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to view this secret code.");
        }

        String decryptedCode = secretEncryptionUtil.decrypt(product.getSecretCode());

        // FIX 2: Add strict no-store caching headers for sensitive data
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(decryptedCode);
    }
}