package hr.kronos.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
    HttpStatusCode statusCode = exception.getStatusCode();
    String error = resolveError(statusCode);
    String reason = exception.getReason();
    String message = reason == null || reason.isBlank() ? error : reason;
    return ResponseEntity.status(statusCode).body(new ApiErrorResponse(message, statusCode.value(), error));
  }

  private static String resolveError(HttpStatusCode statusCode) {
    if (statusCode instanceof HttpStatus httpStatus) {
      return httpStatus.getReasonPhrase();
    }
    return "HTTP " + statusCode.value();
  }
}
