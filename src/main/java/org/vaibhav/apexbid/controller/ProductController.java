package org.vaibhav.apexbid.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vaibhav.apexbid.dto.ProductResponse;
import org.vaibhav.apexbid.entity.Product;
import org.vaibhav.apexbid.repository.ProductRepository;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
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
}