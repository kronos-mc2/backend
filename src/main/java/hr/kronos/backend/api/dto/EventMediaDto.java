package hr.kronos.backend.api.dto;

public record EventMediaDto(
    String id,
    String mediaType,
    String url,
    String thumbnailUrl,
    String fileName,
    Long byteSize,
    Integer width,
    Integer height,
    int sortOrder) {}
