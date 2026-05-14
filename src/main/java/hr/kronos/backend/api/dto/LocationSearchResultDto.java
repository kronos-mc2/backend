package hr.kronos.backend.api.dto;

public record LocationSearchResultDto(
    String id, String title, String subtitle, CoordinatesDto coordinates, String provider) {}
