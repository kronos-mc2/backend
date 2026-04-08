package hr.kronos.backend.auth.dto;

public record AuthResponse(String accessToken, AuthUserDto user) {}
