package hr.kronos.backend.api.dto;

public record EventParticipantDto(
    String userId,
    String name,
    String avatarUrl,
    String status,
    String joinedAt,
    String approvedAt,
    boolean blocked) {}
