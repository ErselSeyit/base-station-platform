package io.github.erselseyit.basestation.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaseGlobalExceptionHandlerTest {

    private TestExceptionHandler handler;
    private WebRequest request;

    static class TestExceptionHandler extends BaseGlobalExceptionHandler {
    }

    @BeforeEach
    void setUp() {
        handler = new TestExceptionHandler();
        request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/test");
    }

    @Test
    void handleIllegalArgumentReturns400WithErrorMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Station ID cannot be null");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid request");
        assertThat(response.getBody().getDetails()).isEqualTo("Station ID cannot be null");
    }

    @Test
    void handleGenericReturns500WithoutLeakingDetails() {
        RuntimeException ex = new RuntimeException("Database connection pool exhausted: host=10.0.0.5, port=5432");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getDetails()).doesNotContain("Database connection pool");
        assertThat(response.getBody().getDetails()).doesNotContain("10.0.0.5");
        assertThat(response.getBody().getDetails()).contains("Please contact support with error ID:");
    }

    @Test
    void handleValidationReturns400WithFieldErrors() {
        FieldError nameError = new FieldError("target", "name", "must not be blank");
        FieldError stationIdError = new FieldError("target", "stationId", "must be positive");

        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(nameError, stationIdError));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        assertThat(response.getBody().getDetails()).contains("name");
        assertThat(response.getBody().getDetails()).contains("must not be blank");
        assertThat(response.getBody().getDetails()).contains("stationId");
        assertThat(response.getBody().getDetails()).contains("must be positive");
    }

    @Test
    void errorIdIs8CharactersLong() {
        String errorId = handler.generateErrorId();

        assertThat(errorId).hasSize(8);
    }

    @Test
    void errorIdIsUniqueAcrossCalls() {
        String errorId1 = handler.generateErrorId();
        String errorId2 = handler.generateErrorId();

        assertThat(errorId1).isNotEqualTo(errorId2);
    }

    @Test
    void pathIsExtractedCorrectlyFromWebRequest() {
        String path = handler.extractPath(request);

        assertThat(path).isEqualTo("/api/test");
    }

    @Test
    void pathExtractionRemovesUriPrefix() {
        when(request.getDescription(false)).thenReturn("uri=/api/v1/stations/42");

        String path = handler.extractPath(request);

        assertThat(path).isEqualTo("/api/v1/stations/42");
    }

    @Test
    void handleIllegalArgumentIncludesPathInErrorResponse() {
        IllegalArgumentException ex = new IllegalArgumentException("bad input");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getPath()).isEqualTo("/api/test");
    }

    @Test
    void handleGenericIncludesErrorIdInResponse() {
        RuntimeException ex = new RuntimeException("oops");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorId()).isNotNull();
        assertThat(response.getBody().getErrorId()).hasSize(8);
    }

    @Test
    void handleIllegalArgumentIncludesTimestamp() {
        IllegalArgumentException ex = new IllegalArgumentException("bad input");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(ex, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }
}
