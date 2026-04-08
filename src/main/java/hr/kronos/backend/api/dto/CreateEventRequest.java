package hr.kronos.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateEventRequest(
    String titleHr,
    String titleEn,
    String whereHr,
    String whereEn,
    String aboutHr,
    String aboutEn,
    @JsonProperty("whenISO") String whenISO,
    CoordinatesDto coordinates,
    CoordinatesDto entranceCoordinates,
    String entryInstructionsHr,
    String entryInstructionsEn,
    String visibility) {}
