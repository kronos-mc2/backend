package hr.kronos.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record UpdateEventRequest(
    String title,
    String where,
    String address,
    String about,
    @JsonProperty("whenISO") String whenISO,
    String startAt,
    String endAt,
    CoordinatesDto coordinates,
    CoordinatesDto entranceCoordinates,
    String entryInstructions,
    String visibility,
    String attendanceMode,
    BigDecimal priceAmount,
    String priceCurrency,
    Integer capacity,
    String status,
    List<String> tags) {}
