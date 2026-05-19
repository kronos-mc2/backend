package hr.kronos.backend.api.dto;

public record RegisterPushTokenRequest(String token, String platform, String deviceId, String locale) {}
