package org.vaibhav.apexbid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vaibhav.apexbid.entity.Product;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByBuyerId(Long buyerId);

    List<Product> findBySellerId(Long sellerId);
}
