package hr.kronos.backend.api.dto;

public record AppNotificationDto(
    String id,
    String type,
    String title,
    String body,
    String eventId,
    String createdAt,
    String readAt) {}
