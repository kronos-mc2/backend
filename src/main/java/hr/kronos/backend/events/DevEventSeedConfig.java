package hr.kronos.backend.events;

import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class DevEventSeedConfig {

  @Bean
  ApplicationRunner seedPublicEventsForDev(EventMapper eventMapper) {
    return (args) -> {
      if (eventMapper.countAllEvents() > 0) {
        return;
      }

      List<EventRow> devEvents =
          List.of(
              buildEvent(
                  "dev-1",
                  "Afterwork kod Zrinjevca",
                  "Afterwork at Zrinjevac",
                  "Zrinjevac, Zagreb",
                  "Zrinjevac, Zagreb",
                  "Lezerno druzenje nakon posla.",
                  "Casual meetup after work.",
                  "2026-05-05T18:00:00Z",
                  "nearby",
                  45.8115,
                  15.9798,
                  "public",
                  24),
              buildEvent(
                  "dev-2",
                  "Sunset DJ Session",
                  "Sunset DJ Session",
                  "Jarun, Zagreb",
                  "Jarun, Zagreb",
                  "Open-air DJ i chill zona.",
                  "Open-air DJ with a chill zone.",
                  "2026-05-12T19:30:00Z",
                  "joined",
                  45.7851,
                  15.9294,
                  "public",
                  61),
              buildEvent(
                  "dev-3",
                  "Private rooftop test",
                  "Private rooftop test",
                  "Centar, Zagreb",
                  "Downtown, Zagreb",
                  "Privatni event za test filtriranja.",
                  "Private event for visibility filter testing.",
                  "2026-05-20T20:00:00Z",
                  "created",
                  45.8144,
                  15.9780,
                  "friends",
                  12));

      for (EventRow event : devEvents) {
        eventMapper.insert(event);
      }
    };
  }

  private EventRow buildEvent(
      String id,
      String titleHr,
      String titleEn,
      String whereHr,
      String whereEn,
      String aboutHr,
      String aboutEn,
      String whenIso,
      String eventType,
      double latitude,
      double longitude,
      String visibility,
      int participants) {
    EventRow row = new EventRow();
    row.setId(id);
    row.setTitleHr(titleHr);
    row.setTitleEn(titleEn);
    row.setWhereHr(whereHr);
    row.setWhereEn(whereEn);
    row.setAddress(whereHr);
    row.setAboutHr(aboutHr);
    row.setAboutEn(aboutEn);
    OffsetDateTime startAt = OffsetDateTime.parse(whenIso);
    row.setWhenIso(startAt);
    row.setStartAt(startAt);
    row.setEventType(eventType);
    row.setLatitude(latitude);
    row.setLongitude(longitude);
    row.setVisibility(visibility);
    row.setAttendanceMode("open");
    row.setStatus("published");
    row.setOrganizerRatingAverage(BigDecimal.ZERO);
    row.setOrganizerRatingCount(0);
    row.setParticipantCount(participants);
    return row;
  }
}
