package hr.kronos.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppEventDto(
    String id,
    String creatorUserId,
    LocalizedTextDto title,
    @JsonProperty("where") LocalizedTextDto whereText,
    String address,
    LocalizedTextDto about,
    @JsonProperty("whenISO") String whenISO,
    String startAt,
    String endAt,
    String type,
    CoordinatesDto coordinates,
    CoordinatesDto entranceCoordinates,
    LocalizedTextDto entryInstructions,
    String visibility,
    String attendanceMode,
    BigDecimal priceAmount,
    String priceCurrency,
    Integer capacity,
    String status,
    java.math.BigDecimal eventRatingAverage,
    int eventRatingCount,
    BigDecimal organizerRatingAverage,
    int organizerRatingCount,
    int likeCount,
    Boolean likedByMe,
    int participantCount,
    int waitlistCount,
    Boolean joinedByMe,
    String attendanceStatus,
    boolean canJoin,
    List<EventMediaDto> media) {}
