package com.onafriq.paylite.service.paylite_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;
    
    @Column(name = "payment_id", nullable = false)
    private String paymentId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        this.processedAt = LocalDateTime.now();
    }
}
