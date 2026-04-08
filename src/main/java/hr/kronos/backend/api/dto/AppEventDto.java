package hr.kronos.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppEventDto(
    String id,
    LocalizedTextDto title,
    @JsonProperty("where") LocalizedTextDto whereText,
    LocalizedTextDto about,
    @JsonProperty("whenISO") String whenISO,
    String type,
    CoordinatesDto coordinates,
    CoordinatesDto entranceCoordinates,
    LocalizedTextDto entryInstructions,
    String visibility,
    int participantCount) {}
