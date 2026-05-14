package hr.kronos.backend.api.dto;

import java.util.List;

public record ProfileActivityDto(
    List<AppEventDto> joinedEvents,
    List<AppEventDto> likedEvents,
    List<AppEventDto> ratingCandidates,
    List<TransactionDto> transactions,
    List<AppNotificationDto> notifications) {}
