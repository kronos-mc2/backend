package hr.kronos.backend.api.dto;

public record FeedPreferenceDto(
    String id,
    String type,
    String targetId,
    String label,
    String createdAt) {}
