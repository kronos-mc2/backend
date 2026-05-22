package hr.kronos.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record CreateEventRequest(
    String title,
    String titleHr,
    String titleEn,
    String where,
    String whereHr,
    String whereEn,
    String address,
    String about,
    String aboutHr,
    String aboutEn,
    @JsonProperty("whenISO") String whenISO,
    String startAt,
    String endAt,
    CoordinatesDto coordinates,
    CoordinatesDto entranceCoordinates,
    String entryInstructions,
    String entryInstructionsHr,
    String entryInstructionsEn,
    String visibility,
    String attendanceMode,
    BigDecimal priceAmount,
    String priceCurrency,
    Integer capacity,
    List<String> tags,
    List<EventMediaRequest> media) {}
