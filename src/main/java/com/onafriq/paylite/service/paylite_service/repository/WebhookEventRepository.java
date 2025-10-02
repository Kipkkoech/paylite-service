package com.onafriq.paylite.service.paylite_service.repository;

import com.onafriq.paylite.service.paylite_service.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    Optional<WebhookEvent> findByEventId(String eventId);
    boolean existsByPaymentId(String paymentId);
    boolean existsByPaymentIdAndEventType(String paymentId, String eventType);
    List<WebhookEvent> findByPaymentIdOrderByProcessedAtDesc(String paymentId);
    WebhookEvent findByPaymentId(String paymentId);
    WebhookEvent findByPaymentIdAndEventType(String paymentId, String eventType);
}