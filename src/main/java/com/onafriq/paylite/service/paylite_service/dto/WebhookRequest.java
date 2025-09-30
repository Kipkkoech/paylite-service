package com.onafriq.paylite.service.paylite_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WebhookRequest {
    @NotBlank(message = "Payment ID is required")
    private String paymentId;
    
    @NotBlank(message = "Event is required")
    private String event;
}