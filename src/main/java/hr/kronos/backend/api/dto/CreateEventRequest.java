package hr.kronos.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record CreateEventRequest(
    String titleHr,
    String titleEn,
    String whereHr,
    String whereEn,
    String address,
    String aboutHr,
    String aboutEn,
    @JsonProperty("whenISO") String whenISO,
    String startAt,
    String endAt,
    CoordinatesDto coordinates,
    CoordinatesDto entranceCoordinates,
    String entryInstructionsHr,
    String entryInstructionsEn,
    String visibility,
    String attendanceMode,
    BigDecimal priceAmount,
    String priceCurrency,
    Integer capacity) {}
