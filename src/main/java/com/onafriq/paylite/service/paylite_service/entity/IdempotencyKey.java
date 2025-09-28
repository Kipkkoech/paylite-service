package com.onafriq.paylite.service.paylite_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String key;
    
    @Column(name = "request_hash", nullable = false)
    private String requestHash;
    
    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;
    
    @Column(name = "payment_id", nullable = false)
    private String paymentId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
