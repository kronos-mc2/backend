package hr.kronos.backend.api.dto;

public record EventRatingRequest(
    Integer eventRating,
    Integer organizerRating,
    String eventComment,
    String organizerComment) {}
