package com.pidabrow.starter.sample.repository;

import com.pidabrow.starter.sample.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for Product entity.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
