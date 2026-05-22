package hr.kronos.backend.api;

public record ApiErrorResponse(String message, int status, String error) {}
