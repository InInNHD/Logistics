package com.firefly.warehouse;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@MappedSuperclass
abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable = false, updatable = false) LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) LocalDateTime updatedAt = LocalDateTime.now();
    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}
