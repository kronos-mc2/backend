package hr.kronos.backend.api.dto;

public record CreateFeedPreferenceRequest(String type, String targetId, String label) {}
