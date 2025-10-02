package com.onafriq.paylite.service.paylite_service.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

import static com.onafriq.paylite.service.paylite_service.config.AppConstants.CORRELATION_ID_HEADER;
import static com.onafriq.paylite.service.paylite_service.config.AppConstants.MDC_CORRELATION_ID_KEY;

@Component
public class CorrelationIdInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {


        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }


        MDC.put(MDC_CORRELATION_ID_KEY, correlationId);

        // Add it to response header so clients can see it
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        return true; // proceed with the request
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // Always remove MDC to avoid memory leaks
        MDC.remove(MDC_CORRELATION_ID_KEY);
    }
}
