package hr.kronos.backend.api.dto;

public record UpdateNotificationPreferencesRequest(Boolean directMessagesEnabled, Boolean groupMessagesEnabled) {}
