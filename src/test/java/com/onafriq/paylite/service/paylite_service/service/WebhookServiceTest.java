package com.onafriq.paylite.service.paylite_service.service;

import com.onafriq.paylite.service.paylite_service.dto.WebhookRequest;
import com.onafriq.paylite.service.paylite_service.entity.WebhookEvent;
import com.onafriq.paylite.service.paylite_service.exception.WebhookConflictException;
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

    // ======== HELPER METHODS ========
    private void setupNonDuplicateWebhook(String requestId) {
        when(httpServletRequest.getHeader("X-Request-Id")).thenReturn(requestId);
        when(webhookEventRepository.findByPaymentId(anyString())).thenReturn(null);
        when(webhookEventRepository.findByPaymentIdAndEventType(anyString(), anyString())).thenReturn(null);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());
    }

    private void setupDuplicateWebhookSamePayload() {
        WebhookEvent duplicate = WebhookEvent.builder()
                .paymentId(PAYMENT_ID)
                .eventType(EVENT_SUCCEEDED)
                .rawPayload(rawBody)
                .eventId("existing-event-001")
                .build();
        when(webhookEventRepository.findByPaymentId(PAYMENT_ID)).thenReturn(duplicate);
    }

    private void setupDuplicateWebhookDifferentPayload() {
        WebhookEvent duplicate = WebhookEvent.builder()
                .paymentId(PAYMENT_ID)
                .eventType(EVENT_SUCCEEDED)
                .rawPayload("{\"different\":\"payload\"}")
                .eventId("existing-event-002")
                .build();
        when(webhookEventRepository.findByPaymentId(PAYMENT_ID)).thenReturn(duplicate);
    }

    private void assertSavedWebhookEvent(String expectedEventId, String expectedPaymentId, String expectedEventType, String expectedPayload) {
        verify(webhookEventRepository).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();
        assertEquals(expectedEventId, savedEvent.getEventId());
        assertEquals(expectedPaymentId, savedEvent.getPaymentId());
        assertEquals(expectedEventType, savedEvent.getEventType());
        assertEquals(expectedPayload, savedEvent.getRawPayload());
    }

    // ======== PROCESS WEBHOOK TESTS ========
    @Test
    void processWebhook_WithValidSucceededEvent_ShouldProcessSuccessfully() {
        setupNonDuplicateWebhook("req-123");

        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        verify(paymentService).processWebhook(PAYMENT_ID, EVENT_SUCCEEDED);
        verify(webhookEventRepository).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithValidFailedEvent_ShouldProcessSuccessfully() {
        webhookRequest.setEvent(EVENT_FAILED);
        setupNonDuplicateWebhook("req-456");

        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        verify(paymentService).processWebhook(PAYMENT_ID, EVENT_FAILED);
        verify(webhookEventRepository).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithInvalidEventType_ShouldThrowException() {
        webhookRequest.setEvent("payment.pending");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest));

        assertTrue(exception.getMessage().contains("Invalid event type"));
        verify(paymentService, never()).processWebhook(anyString(), anyString());
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithDuplicateSamePayload_ShouldReturnWithoutProcessing() {
        setupDuplicateWebhookSamePayload();

        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        verify(paymentService, never()).processWebhook(anyString(), anyString());
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithDuplicateDifferentPayload_ShouldThrowConflict() {
        setupDuplicateWebhookDifferentPayload();

        assertThrows(WebhookConflictException.class,
                () -> webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest));

        verify(paymentService, never()).processWebhook(anyString(), anyString());
        verify(webhookEventRepository, never()).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithoutRequestIdHeader_ShouldGenerateFallbackEventId() {
        setupNonDuplicateWebhook(null);

        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        verify(webhookEventRepository).save(webhookEventCaptor.capture());
        WebhookEvent savedEvent = webhookEventCaptor.getValue();
        assertTrue(savedEvent.getEventId().startsWith("psp_" + PAYMENT_ID));
        assertTrue(savedEvent.getEventId().contains(EVENT_SUCCEEDED));
    }

    @Test
    void processWebhook_ShouldSaveEventWithCorrectPayload() {
        setupNonDuplicateWebhook("req-999");
        String customRawBody = "{\"custom\":\"payload\"}";

        webhookService.processWebhook(webhookRequest, customRawBody, httpServletRequest);

        assertSavedWebhookEventStartsWith("psp_", PAYMENT_ID, EVENT_SUCCEEDED, customRawBody);
    }

    private void assertSavedWebhookEventStartsWith(String eventIdPrefix, String paymentId, String eventType, String payload) {
        verify(webhookEventRepository).save(webhookEventCaptor.capture());
        WebhookEvent saved = webhookEventCaptor.getValue();
        assertTrue(saved.getEventId().startsWith(eventIdPrefix));
        assertEquals(paymentId, saved.getPaymentId());
        assertEquals(eventType, saved.getEventType());
        assertEquals(payload, saved.getRawPayload());
    }

    // ======== RECORD WEBHOOK EVENT TESTS ========
    @Test
    void recordWebhookEvent_ShouldSaveEventWithCorrectData() {
        String eventId = "psp_req-123";
        webhookService.recordWebhookEvent(eventId, PAYMENT_ID, EVENT_SUCCEEDED, rawBody);

        assertSavedWebhookEvent(eventId, PAYMENT_ID, EVENT_SUCCEEDED, rawBody);
    }

    @Test
    void recordWebhookEvent_WithFailedEvent_ShouldSaveCorrectly() {
        String eventId = "psp_req-failed-001";
        String failedRawBody = "{\"paymentId\":\"payment-123\",\"event\":\"payment.failed\"}";

        webhookService.recordWebhookEvent(eventId, PAYMENT_ID, EVENT_FAILED, failedRawBody);

        assertSavedWebhookEvent(eventId, PAYMENT_ID, EVENT_FAILED, failedRawBody);
    }

    // ======== INTEGRATION FLOW TESTS ========
    @Test
    void processWebhook_CompleteFlow_ShouldExecuteAllSteps() {
        String requestId = "req-flow-001";
        setupNonDuplicateWebhook(requestId);

        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        verify(webhookEventRepository).findByPaymentId(PAYMENT_ID);
        verify(webhookEventRepository).findByPaymentIdAndEventType(PAYMENT_ID, EVENT_SUCCEEDED);
        verify(paymentService).processWebhook(PAYMENT_ID, EVENT_SUCCEEDED);
        verify(webhookEventRepository).save(any(WebhookEvent.class));
    }

    @Test
    void processWebhook_WithMultipleValidEvents_ShouldProcessEach() {
        when(httpServletRequest.getHeader("X-Request-Id"))
                .thenReturn("req-001")
                .thenReturn("req-002");
        when(webhookEventRepository.findByPaymentId(anyString())).thenReturn(null);
        when(webhookEventRepository.findByPaymentIdAndEventType(anyString(), anyString())).thenReturn(null);
        doNothing().when(paymentService).processWebhook(anyString(), anyString());

        // First webhook
        webhookService.processWebhook(webhookRequest, rawBody, httpServletRequest);

        // Second webhook
        WebhookRequest failedRequest = new WebhookRequest();
        failedRequest.setPaymentId(PAYMENT_ID);
        failedRequest.setEvent(EVENT_FAILED);
        webhookService.processWebhook(failedRequest, rawBody, httpServletRequest);

        verify(paymentService).processWebhook(PAYMENT_ID, EVENT_SUCCEEDED);
        verify(paymentService).processWebhook(PAYMENT_ID, EVENT_FAILED);
        verify(webhookEventRepository, times(2)).save(any(WebhookEvent.class));
    }

    // ======== IS DUPLICATE WEBHOOK TESTS ========
    @Test
    void isDuplicateWebhook_WithNoExistingRecord_ShouldReturnFalse() {
        when(webhookEventRepository.existsByPaymentId(anyString())).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(anyString(), anyString())).thenReturn(false);

        boolean result = webhookService.isDuplicateWebhook("event-123", PAYMENT_ID, EVENT_SUCCEEDED);

        assertFalse(result);
    }

    @Test
    void isDuplicateWebhook_WithExistingPaymentId_ShouldReturnTrue() {
        when(webhookEventRepository.existsByPaymentId(PAYMENT_ID)).thenReturn(true);

        boolean result = webhookService.isDuplicateWebhook("event-123", PAYMENT_ID, EVENT_SUCCEEDED);

        assertTrue(result);
    }

    @Test
    void isDuplicateWebhook_WithExistingBusinessKey_ShouldReturnTrue() {
        when(webhookEventRepository.existsByPaymentId(PAYMENT_ID)).thenReturn(false);
        when(webhookEventRepository.existsByPaymentIdAndEventType(PAYMENT_ID, EVENT_SUCCEEDED)).thenReturn(true);

        boolean result = webhookService.isDuplicateWebhook("event-123", PAYMENT_ID, EVENT_SUCCEEDED);

        assertTrue(result);
    }
}
