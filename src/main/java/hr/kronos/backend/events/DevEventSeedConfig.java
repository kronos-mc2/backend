package hr.kronos.backend.events;

import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventMediaRow;
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
  private static final String MEDIA_TYPE_IMAGE = "image";
  private static final String MEDIA_TYPE_VIDEO = "video";
  private static final String PRIMARY_MEDIA_SUFFIX = "-media-1";

  @Bean
  ApplicationRunner seedPublicEventsForDev(EventMapper eventMapper) {
    return args -> {
      if (eventMapper.countAllEvents() > 0) {
        return;
      }

      List<EventRow> devEvents =
          List.of(
              buildEvent(
                  new DevEventSeed(
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
                      24)),
              buildEvent(
                  new DevEventSeed(
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
                      61)),
              buildEvent(
                  new DevEventSeed(
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
                      12)));

      for (EventRow event : devEvents) {
        eventMapper.insert(event);
        for (EventMediaRow media : buildMedia(event.getId())) {
          eventMapper.insertMedia(media);
        }
      }
    };
  }

  private EventRow buildEvent(DevEventSeed seed) {
    EventRow row = new EventRow();
    row.setId(seed.id());
    row.setTitleHr(seed.titleHr());
    row.setTitleEn(seed.titleEn());
    row.setWhereHr(seed.whereHr());
    row.setWhereEn(seed.whereEn());
    row.setAddress(seed.whereHr());
    row.setAboutHr(seed.aboutHr());
    row.setAboutEn(seed.aboutEn());
    OffsetDateTime startAt = OffsetDateTime.parse(seed.whenIso());
    row.setWhenIso(startAt);
    row.setStartAt(startAt);
    row.setEventType(seed.eventType());
    row.setLatitude(seed.latitude());
    row.setLongitude(seed.longitude());
    row.setVisibility(seed.visibility());
    row.setAttendanceMode("open");
    row.setStatus("published");
    row.setEventRatingAverage(BigDecimal.ZERO);
    row.setEventRatingCount(0);
    row.setOrganizerRatingAverage(BigDecimal.ZERO);
    row.setOrganizerRatingCount(0);
    row.setParticipantCount(seed.participants());
    return row;
  }

  private List<EventMediaRow> buildMedia(String eventId) {
    return switch (eventId) {
      case "dev-1" -> List.of(
          media(
              eventId + PRIMARY_MEDIA_SUFFIX,
              eventId,
              MEDIA_TYPE_VIDEO,
              "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
              "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1200&q=80",
              0),
          media(
              eventId + "-media-2",
              eventId,
              MEDIA_TYPE_IMAGE,
              "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1200&q=80",
              null,
              1));
      case "dev-2" -> List.of(
          media(
              eventId + PRIMARY_MEDIA_SUFFIX,
              eventId,
              MEDIA_TYPE_VIDEO,
              "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
              "https://images.unsplash.com/photo-1506157786151-b8491531f063?auto=format&fit=crop&w=1200&q=80",
              0),
          media(
              eventId + "-media-2",
              eventId,
              MEDIA_TYPE_IMAGE,
              "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=1200&q=80",
              null,
              1));
      case "dev-3" -> List.of(
          media(
              eventId + PRIMARY_MEDIA_SUFFIX,
              eventId,
              MEDIA_TYPE_IMAGE,
              "https://images.unsplash.com/photo-1521334884684-d80222895322?auto=format&fit=crop&w=1200&q=80",
              null,
              0));
      default -> List.of();
    };
  }

  private EventMediaRow media(
      String id, String eventId, String mediaType, String url, String thumbnailUrl, int sortOrder) {
    EventMediaRow row = new EventMediaRow();
    row.setId(id);
    row.setEventId(eventId);
    row.setMediaType(mediaType);
    row.setUrl(url);
    row.setThumbnailUrl(thumbnailUrl);
    row.setSortOrder(sortOrder);
    return row;
  }

  private record DevEventSeed(
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
      int participants) {}
}
