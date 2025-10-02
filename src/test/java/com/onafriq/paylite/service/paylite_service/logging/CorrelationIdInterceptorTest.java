package com.onafriq.paylite.service.paylite_service.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

import java.util.UUID;

import static com.onafriq.paylite.service.paylite_service.config.AppConstants.CORRELATION_ID_HEADER;
import static com.onafriq.paylite.service.paylite_service.config.AppConstants.MDC_CORRELATION_ID_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CorrelationIdInterceptorTest {

    private CorrelationIdInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new CorrelationIdInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @AfterEach
    void tearDown() {
        MDC.clear(); // Ensure MDC is clean after each test
    }

    @Test
    void preHandle_shouldUseExistingCorrelationIdFromHeader() {
        String existingId = "existing-correlation-id";
        when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(existingId);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        assertEquals(existingId, MDC.get(MDC_CORRELATION_ID_KEY));
        verify(response).setHeader(CORRELATION_ID_HEADER, existingId);
    }

    @Test
    void preHandle_shouldGenerateNewCorrelationIdWhenHeaderIsMissing() {
        when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        String correlationIdInMDC = MDC.get(MDC_CORRELATION_ID_KEY);
        assertNotNull(correlationIdInMDC);
        assertDoesNotThrow(() -> UUID.fromString(correlationIdInMDC)); // valid UUID
        verify(response).setHeader(CORRELATION_ID_HEADER, correlationIdInMDC);
    }

    @Test
    void preHandle_shouldGenerateNewCorrelationIdWhenHeaderIsEmpty() {
        when(request.getHeader(CORRELATION_ID_HEADER)).thenReturn("");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        String correlationIdInMDC = MDC.get(MDC_CORRELATION_ID_KEY);
        assertNotNull(correlationIdInMDC);
        assertDoesNotThrow(() -> UUID.fromString(correlationIdInMDC)); // valid UUID
        verify(response).setHeader(CORRELATION_ID_HEADER, correlationIdInMDC);
    }

    @Test
    void afterCompletion_shouldRemoveMdcKey() {
        // Put a value first
        MDC.put(MDC_CORRELATION_ID_KEY, "test-id");

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(MDC.get(MDC_CORRELATION_ID_KEY));
    }
}
