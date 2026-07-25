package dev.aparadhkavach.apigateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.aparadhkavach.commons.header.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void generatesAndEchoesWhenMissing() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(HeaderConstants.X_CORRELATION_ID)).thenReturn(null);
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    filter.doFilter(request, response, chain);

    ArgumentCaptor<HttpServletRequest> forwarded =
        ArgumentCaptor.forClass(HttpServletRequest.class);
    verify(chain).doFilter(forwarded.capture(), any(HttpServletResponse.class));
    String generated = forwarded.getValue().getHeader(HeaderConstants.X_CORRELATION_ID);
    assertNotNull(generated);
    assertTrue(isUuid(generated));
    verify(response).setHeader(HeaderConstants.X_CORRELATION_ID, generated);
  }

  @Test
  void reusesAndEchoesInboundValue() throws Exception {
    String inbound = "client-corr-42";
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(HeaderConstants.X_CORRELATION_ID)).thenReturn(inbound);

    filter.doFilter(request, response, chain);

    ArgumentCaptor<HttpServletRequest> forwarded =
        ArgumentCaptor.forClass(HttpServletRequest.class);
    verify(chain).doFilter(forwarded.capture(), any(HttpServletResponse.class));
    assertEquals(request, forwarded.getValue());
    verify(response).setHeader(HeaderConstants.X_CORRELATION_ID, inbound);
  }

  @Test
  void treatsBlankAsMissing() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader(HeaderConstants.X_CORRELATION_ID)).thenReturn("   ");
    when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

    filter.doFilter(request, response, chain);

    ArgumentCaptor<HttpServletRequest> forwarded =
        ArgumentCaptor.forClass(HttpServletRequest.class);
    verify(chain).doFilter(forwarded.capture(), any(HttpServletResponse.class));
    String generated = forwarded.getValue().getHeader(HeaderConstants.X_CORRELATION_ID);
    assertTrue(isUuid(generated));
    verify(response).setHeader(HeaderConstants.X_CORRELATION_ID, generated);
  }

  private static boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
