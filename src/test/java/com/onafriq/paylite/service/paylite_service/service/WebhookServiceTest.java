package com.onafriq.paylite.service.paylite_service.service;

import com.onafriq.paylite.service.paylite_service.dto.WebhookRequest;
import com.onafriq.paylite.service.paylite_service.entity.WebhookEvent;
import com.onafriq.paylite.service.paylite_service.repository.WebhookEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private WebhookService webhookService;

    @Captor
    private ArgumentCaptor<WebhookEvent> webhookEventCaptor;

    private WebhookRequest webhookRequest;
    private String rawBody;
    private static final String PAYMENT_ID = "payment-123";
    private static final String EVENT_SUCCEEDED = "payment.succeeded";
    private static final String EVENT_FAILED = "payment.failed";

    @BeforeEach
    void setUp() {
        webhookRequest = new WebhookRequest();
        webhookRequest.setPaymentId(PAYMENT_ID);
        webhookRequest.setEvent(EVENT_SUCCEEDED);
        rawBody = "{\"paymentId\":\"payment-123\",\"event\":\"payment.succeeded\"}";
    }

    // ===== PROCESS WEBHOOK TESTS =====

    @Test
    void processWebhook_WithValidSucceededEvent_ShouldProcessSuccessfully() {
        // Arrange
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn("req-123");
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(anyString(), anyString())).thenReturn(false);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());

        // Act
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Assert
        verify(paymentService).processWebhook(PAYMENT_ID, EVENT_SUCCEEDED);
        verify(webhookEventRepository).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithValidFailedEvent_ShouldProcessSuccessfully() {
        // Arrange
        webhookRequest.setEvent(EVENT_FAILED);
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn("req-456");
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(anyString(), anyString())).thenReturn(false);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());

        // Act
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Assert
        verify(paymentService).processWebhook(PAYMENT_ID, EVENT_FAILED);
        verify(webhookEventRepository).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithInvalidEventType_ShouldThrowException() {
        // Arrange
        webhookRequest.setEvent("payment.pending");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest)
        );

        assertTrue(exception.getMessage().contains("Invalid event type"));
        assertTrue(exception.getMessage().contains("payment.pending"));
        verify(paymentService, never()).processWebhook(anyString(), anyString());
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithDuplicateEventId_ShouldNotProcess() {
        // Arrange
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn("req-123");
        when(webhookEventRepository.existsByEventId("psp_req-123")).thenReturn(true);

        // Act
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Assert
        verify(paymentService, never()).processWebhook(anyString(), anyString());
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithDuplicateBusinessKey_ShouldNotProcess() {
        // Arrange
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn("req-123");
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(PAYMENT_ID, EVENT_SUCCEEDED)).thenReturn(true);

        // Act
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Assert
        verify(paymentService, never()).processWebhook(anyString(), anyString());
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithoutRequestIdHeader_ShouldGenerateFallbackEventId() {
        // Arrange
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn(null);
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(anyString(), anyString())).thenReturn(false);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());

        // Act
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Assert
        verify(webhookEventRepository).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();

        assertTrue(savedEvent.getEventId().startsWith("psp_" + PAYMENT_ID));
        assertTrue(savedEvent.getEventId().contains(EVENT_SUCCEEDED));
    }

    @Test
    void processWebhook_WithEmptyRequestIdHeader_ShouldGenerateFallbackEventId() {
        // Arrange
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn("   ");
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(anyString(), anyString())).thenReturn(false);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());

        // Act
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Assert
        verify(webhookEventRepository).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();

        assertTrue(savedEvent.getEventId().startsWith("psp_" + PAYMENT_ID));
    }

    @Test
    void processWebhook_ShouldRecordEventWithCorrectPayload() {
        // Arrange
        String customRawBody = "{\"custom\":\"payload\"}";
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn("req-999");
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(anyString(), anyString())).thenReturn(false);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());

        // Act
        webhookService.processWebhook(webhookRequest, customRawBody, httpServletRequest);

        // Assert
        verify(webhookEventRepository).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();

        assertEquals(customRawBody, savedEvent.getRawPayload());
    }

    @Test
    void processWebhook_ShouldUseRequestIdInEventId() {
        // Arrange
        String requestId = "unique-request-id-789";
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn(requestId);
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(anyString(), anyString())).thenReturn(false);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());

        // Act
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Assert
        verify(webhookEventRepository).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();

        assertEquals("psp_" + requestId, savedEvent.getEventId());
    }

    @Test
    void processWebhook_ShouldSaveEventWithAllFields() {
        // Arrange
        String requestId = "req-complete-001";
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn(requestId);
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(anyString(), anyString())).thenReturn(false);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());

        // Act
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Assert
        verify(webhookEventRepository).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();

        assertEquals("psp_" + requestId, savedEvent.getEventId());
        assertEquals(PAYMENT_ID, savedEvent.getPaymentId());
        assertEquals(EVENT_SUCCEEDED, savedEvent.getEventType());
        assertEquals(rawBody, savedEvent.getRawPayload());
    }

    // ===== IS DUPLICATE WEBHOOK TESTS =====

    @Test
    void isDuplicateWebhook_WithExistingEventId_ShouldReturnTrue() {
        // Arrange
        String eventId = "psp_req-123";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(true);

        // Act
        boolean result = webhookService.isDuplicateWebhook(eventId, PAYMENT_ID, EVENT_SUCCEEDED);

        // Assert
        assertTrue(result);
        verify(webhookEventRepository).existsByEventId(eventId);
    }

    @Test
    void isDuplicateWebhook_WithExistingBusinessKey_ShouldReturnTrue() {
        // Arrange
        String eventId = "psp_req-123";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(PAYMENT_ID, EVENT_SUCCEEDED)).thenReturn(true);

        // Act
        boolean result = webhookService.isDuplicateWebhook(eventId, PAYMENT_ID, EVENT_SUCCEEDED);

        // Assert
        assertTrue(result);
        verify(webhookEventRepository).existsByPaymentIdAndEventType(PAYMENT_ID, EVENT_SUCCEEDED);
    }

    @Test
    void isDuplicateWebhook_WithNoExistingRecord_ShouldReturnFalse() {
        // Arrange
        String eventId = "psp_req-123";
        when(webhookEventRepository.existsByEventId(eventId)).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(PAYMENT_ID, EVENT_SUCCEEDED)).thenReturn(false);

        // Act
        boolean result = webhookService.isDuplicateWebhook(eventId, PAYMENT_ID, EVENT_SUCCEEDED);

        // Assert
        assertFalse(result);
    }

    @Test
    void isDuplicateWebhook_WithNullEventId_ShouldCheckBusinessKeyOnly() {
        // Arrange
        when(webhookEventRepository.existsByPaymentIdAndEventType(PAYMENT_ID, EVENT_SUCCEEDED)).thenReturn(false);

        // Act
        boolean result = webhookService.isDuplicateWebhook(null, PAYMENT_ID, EVENT_SUCCEEDED);

        // Assert
        assertFalse(result);
        verify(webhookEventRepository, never()).existsByEventId(any());
        verify(webhookEventRepository).existsByPaymentIdAndEventType(PAYMENT_ID, EVENT_SUCCEEDED);
    }

    @Test
    void isDuplicateWebhook_WithNullEventIdAndExistingBusinessKey_ShouldReturnTrue() {
        // Arrange
        when(webhookEventRepository.existsByPaymentIdAndEventType(PAYMENT_ID, EVENT_SUCCEEDED)).thenReturn(true);

        // Act
        boolean result = webhookService.isDuplicateWebhook(null, PAYMENT_ID, EVENT_SUCCEEDED);

        // Assert
        assertTrue(result);
        verify(webhookEventRepository, never()).existsByEventId(any());
    }

    // ===== RECORD WEBHOOK EVENT TESTS =====

    @Test
    void recordWebhookEvent_ShouldSaveEventWithCorrectData() {
        // Arrange
        String eventId = "psp_req-123";

        // Act
        webhookService.recordWebhookEvent(eventId, PAYMENT_ID, EVENT_SUCCEEDED, rawBody);

        // Assert
        verify(webhookEventRepository).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();

        assertEquals(eventId, savedEvent.getEventId());
        assertEquals(PAYMENT_ID, savedEvent.getPaymentId());
        assertEquals(EVENT_SUCCEEDED, savedEvent.getEventType());
        assertEquals(rawBody, savedEvent.getRawPayload());
    }

    @Test
    void recordWebhookEvent_WithFailedEvent_ShouldSaveCorrectly() {
        // Arrange
        String eventId = "psp_req-failed-001";
        String failedRawBody = "{\"paymentId\":\"payment-123\",\"event\":\"payment.failed\"}";

        // Act
        webhookService.recordWebhookEvent(eventId, PAYMENT_ID, EVENT_FAILED, failedRawBody);

        // Assert
        verify(webhookEventRepository).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();

        assertEquals(eventId, savedEvent.getEventId());
        assertEquals(PAYMENT_ID, savedEvent.getPaymentId());
        assertEquals(EVENT_FAILED, savedEvent.getEventType());
        assertEquals(failedRawBody, savedEvent.getRawPayload());
    }

    // ===== INTEGRATION FLOW TESTS =====

    @Test
    void processWebhook_CompleteFlow_ShouldExecuteAllSteps() {
        // Arrange
        String requestId = "req-flow-001";
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn(requestId);
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(anyString(), anyString())).thenReturn(false);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());

        // Act
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Assert - Verify all steps executed in order
        verify(webhookEventRepository).existsByEventId("psp_" + requestId);
        verify(webhookEventRepository).existsByPaymentIdAndEventType(PAYMENT_ID, EVENT_SUCCEEDED);
        verify(paymentService).processWebhook(PAYMENT_ID, EVENT_SUCCEEDED);
        verify(webhookEventRepository).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithMultipleValidEvents_ShouldProcessEach() {
        // Arrange
        when(httpServletRequest.getHeader("X-Request-Id"))
                .thenReturn("req-001")
                .thenReturn("req-002");
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(anyString(), anyString())).thenReturn(false);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());

        // Act - First webhook
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Act - Second webhook (different event type)
        WebhookRequest failedRequest = new WebhookRequest();
        failedRequest.setPaymentId(PAYMENT_ID);
        failedRequest.setEvent(EVENT_FAILED);
        webhookService.processWebhook(failedRequest, rawBody, httpServletRequest);

        // Assert
        verify(paymentService).processWebhook(PAYMENT_ID, EVENT_SUCCEEDED);
        verify(paymentService).processWebhook(PAYMENT_ID, EVENT_FAILED);
        verify(webhookEventRepository, times(2)).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithNullPaymentId_ShouldStillProcess() {
        // Arrange
        webhookRequest.setPaymentId(null);
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn("req-null-001");
        when(webhookEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(any(), anyString())).thenReturn(false);
        doNothing().when(paymentService).processWebhook(any(), anyString());

        // Act & Assert - Should not throw NPE
        assertDoesNotThrow(() ->
                webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest)
        );

        verify(paymentService).processWebhook(null, EVENT_SUCCEEDED);
    }
}