package hr.kronos.backend.api.dto;

public record EventSharePreviewDto(
    String id,
    LocalizedTextDto title,
    LocalizedTextDto where,
    LocalizedTextDto about,
    String whenISO,
    String coverUrl) {}
