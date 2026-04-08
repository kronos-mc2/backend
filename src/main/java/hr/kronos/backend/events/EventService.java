package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CoordinatesDto;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventRow;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {
  private static final String CREATED_EVENT_TYPE = "created";

  private final EventMapper eventMapper;

  public EventService(EventMapper eventMapper) {
    this.eventMapper = eventMapper;
  }

  public List<AppEventDto> getEvents() {
    return eventMapper.findAll().stream().map(this::toDto).toList();
  }

  public List<AppEventDto> getFeed() {
    return eventMapper.findFeed().stream().map(this::toDto).toList();
  }

  public AppEventDto createEvent(CreateEventRequest request) {
    validateRequest(request);

    OffsetDateTime whenIso = parseWhenIso(request.whenISO());
    String visibility = normalizeVisibility(request.visibility());

    EventRow row = new EventRow();
    row.setId("created-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    row.setTitleHr(request.titleHr().trim());
    row.setTitleEn(request.titleEn().trim());
    row.setWhereHr(request.whereHr().trim());
    row.setWhereEn(request.whereEn().trim());
    row.setAboutHr(request.aboutHr().trim());
    row.setAboutEn(request.aboutEn().trim());
    row.setWhenIso(whenIso);
    row.setEventType(CREATED_EVENT_TYPE);
    row.setLatitude(request.coordinates().latitude());
    row.setLongitude(request.coordinates().longitude());
    row.setParticipantCount(1);
    row.setVisibility(visibility);

    if (request.entranceCoordinates() != null) {
      row.setEntranceLatitude(request.entranceCoordinates().latitude());
      row.setEntranceLongitude(request.entranceCoordinates().longitude());
    }

    row.setEntryInstructionsHr(trimToNull(request.entryInstructionsHr()));
    row.setEntryInstructionsEn(trimToNull(request.entryInstructionsEn()));

    eventMapper.insert(row);
    return toDto(row);
  }

  private AppEventDto toDto(EventRow row) {
    CoordinatesDto entranceCoordinates = null;
    if (row.getEntranceLatitude() != null && row.getEntranceLongitude() != null) {
      entranceCoordinates = new CoordinatesDto(row.getEntranceLatitude(), row.getEntranceLongitude());
    }

    LocalizedTextDto entryInstructions = null;
    if (row.getEntryInstructionsHr() != null && row.getEntryInstructionsEn() != null) {
      entryInstructions = new LocalizedTextDto(row.getEntryInstructionsHr(), row.getEntryInstructionsEn());
    }

    return new AppEventDto(
        row.getId(),
        new LocalizedTextDto(row.getTitleHr(), row.getTitleEn()),
        new LocalizedTextDto(row.getWhereHr(), row.getWhereEn()),
        new LocalizedTextDto(row.getAboutHr(), row.getAboutEn()),
        row.getWhenIso().toInstant().toString(),
        row.getEventType(),
        new CoordinatesDto(row.getLatitude(), row.getLongitude()),
        entranceCoordinates,
        entryInstructions,
        row.getVisibility(),
        row.getParticipantCount());
  }

  private void validateRequest(CreateEventRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    requireNonBlank(request.titleHr(), "titleHr");
    requireNonBlank(request.titleEn(), "titleEn");
    requireNonBlank(request.whereHr(), "whereHr");
    requireNonBlank(request.whereEn(), "whereEn");
    requireNonBlank(request.aboutHr(), "aboutHr");
    requireNonBlank(request.aboutEn(), "aboutEn");
    requireNonBlank(request.whenISO(), "whenISO");

    if (request.coordinates() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coordinates are required.");
    }
  }

  private void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required.");
    }
  }

  private OffsetDateTime parseWhenIso(String whenIso) {
    try {
      return OffsetDateTime.parse(whenIso);
    } catch (DateTimeParseException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid whenISO format.");
    }
  }

  private String normalizeVisibility(String visibility) {
    return "private".equalsIgnoreCase(visibility) ? "private" : "public";
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }

    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
