package hr.kronos.backend.api.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
