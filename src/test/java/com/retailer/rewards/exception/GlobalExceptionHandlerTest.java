package com.retailer.rewards.exception;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404Body() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleNotFound(new CustomerNotFoundException(7L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("status", 404)
                .containsEntry("error", "Not Found")
                .containsEntry("message", "Customer not found: 7")
                .containsKey("timestamp");
    }

    @Test
    void handleBadRequest_returns400Body() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleBadRequest(new IllegalArgumentException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "bad input");
    }

    @Test
    void handleTypeMismatch_localDateGivesFriendlyMessage() {
        MethodArgumentTypeMismatchException ex = Mockito.mock(MethodArgumentTypeMismatchException.class);
        Mockito.<Class<?>>when(ex.getRequiredType()).thenReturn(LocalDate.class);
        Mockito.when(ex.getValue()).thenReturn("xyz");
        Mockito.when(ex.getName()).thenReturn("start");

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").toString()).contains("YYYY-MM-DD", "start", "xyz");
    }

    @Test
    void handleTypeMismatch_nonDateGivesGenericMessage() {
        MethodArgumentTypeMismatchException ex = Mockito.mock(MethodArgumentTypeMismatchException.class);
        Mockito.<Class<?>>when(ex.getRequiredType()).thenReturn(Long.class);
        Mockito.when(ex.getValue()).thenReturn("abc");
        Mockito.when(ex.getName()).thenReturn("customerId");

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message").toString()).contains("customerId", "abc");
    }

    @Test
    void handleAll_returns500() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAll(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "boom");
    }

    @Test
    void handleAll_handlesNullMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleAll(new RuntimeException());
        assertThat(response.getBody()).containsEntry("message", "");
    }
}
