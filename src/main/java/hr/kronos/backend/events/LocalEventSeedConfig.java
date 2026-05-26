package hr.kronos.backend.events;

import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventMediaRow;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.storage.ObjectStorageService;
import hr.kronos.backend.storage.StoredObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
@Profile("dev")
public class LocalEventSeedConfig {
  private static final String MEDIA_TYPE_IMAGE = "image";

  @Bean
  @Order(0)
  ApplicationRunner seedLocalEvents(
      EventMapper eventMapper,
      ObjectStorageService objectStorageService,
      @Value("${app.dev-seed.events.csv-path:../Gdje-I-Kada-Native/.local/issue-drafts/test-data/events.normalized.csv}") String csvPath,
      @Value("${app.dev-seed.events.assets-dir:../Gdje-I-Kada-Native/.local/issue-drafts/test-data}") String assetsDir) {
    return _ -> {
      if (eventMapper.countAllEvents() > 0) {
        return;
      }
      Path csv = Path.of(csvPath);
      if (!Files.isRegularFile(csv)) {
        return;
      }

      List<List<String>> rows = parseCsv(Files.readString(csv));
      if (rows.size() <= 1) {
        return;
      }

      for (List<String> row : rows.subList(1, rows.size())) {
        LocalSeedEvent event = LocalSeedEvent.from(row);
        EventRow eventRow = buildEventRow(event);
        eventMapper.insert(eventRow);
        for (String tag : event.tags()) {
          eventMapper.insertTag(event.id(), tag);
        }
        eventMapper.insertMedia(buildMedia(event, Path.of(assetsDir), objectStorageService));
      }
    };
  }

  private EventRow buildEventRow(LocalSeedEvent event) {
    EventRow row = new EventRow();
    row.setId(event.id());
    row.setTitleHr(event.title());
    row.setTitleEn(event.title());
    row.setWhereHr(event.venue());
    row.setWhereEn(event.venue());
    row.setAddress(event.address());
    row.setAboutHr("Lokalni test event importiran iz CSV-a.");
    row.setAboutEn("Local test event imported from CSV.");
    row.setSourceUrl(event.sourceUrl());
    OffsetDateTime startAt = OffsetDateTime.parse(event.startAt());
    row.setWhenIso(startAt);
    row.setStartAt(startAt);
    row.setEndAt(event.endAt().isBlank() ? null : OffsetDateTime.parse(event.endAt()));
    row.setEventType("nearby");
    row.setLatitude(Double.parseDouble(event.latitude()));
    row.setLongitude(Double.parseDouble(event.longitude()));
    row.setVisibility("public");
    row.setAttendanceMode(event.priceAmount().isBlank() ? "open" : "paid");
    row.setPriceAmount(event.priceAmount().isBlank() ? null : new BigDecimal(event.priceAmount()));
    row.setPriceCurrency(event.priceCurrency().isBlank() ? null : event.priceCurrency());
    row.setStatus("published");
    row.setEventRatingAverage(BigDecimal.ZERO);
    row.setEventRatingCount(0);
    row.setOrganizerRatingAverage(BigDecimal.ZERO);
    row.setOrganizerRatingCount(0);
    row.setParticipantCount(0);
    if (!event.entryStartAt().isBlank()) {
      row.setEntryInstructionsHr("Ulaz od " + event.entryStartAt() + "h.");
      row.setEntryInstructionsEn("Entry from " + event.entryStartAt() + ".");
    }
    return row;
  }

  private EventMediaRow buildMedia(LocalSeedEvent event, Path assetsDir, ObjectStorageService objectStorageService) {
    Path imagePath = assetsDir.resolve(event.imageLocal());
    if (objectStorageService.isConfigured() && Files.isRegularFile(imagePath)) {
      try {
        byte[] bytes = Files.readAllBytes(imagePath);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
          throw new IllegalArgumentException("Unreadable seed image.");
        }
        StoredObject stored = objectStorageService.putImage(
            "events/" + event.id() + "/" + UUID.randomUUID().toString().replace("-", "") + ".jpg",
            bytes,
            "image/jpeg",
            image.getWidth(),
            image.getHeight());
        return media(event.id() + "-media-1", event.id(), stored.url(), stored.url(), stored);
      } catch (Exception ignored) {
        return media(event.id() + "-media-1", event.id(), event.imageSourceUrl(), null, null);
      }
    }
    return media(event.id() + "-media-1", event.id(), event.imageSourceUrl(), null, null);
  }

  private EventMediaRow media(String id, String eventId, String url, String thumbnailUrl, StoredObject stored) {
    EventMediaRow row = new EventMediaRow();
    row.setId(id);
    row.setEventId(eventId);
    row.setMediaType(MEDIA_TYPE_IMAGE);
    row.setUrl(url);
    row.setThumbnailUrl(thumbnailUrl);
    row.setSortOrder(0);
    if (stored != null) {
      row.setStorageKey(stored.storageKey());
      row.setBucketName(stored.bucketName());
      row.setContentType(stored.contentType());
      row.setByteSize(stored.byteSize());
      row.setWidth(stored.width());
      row.setHeight(stored.height());
    }
    return row;
  }

  private List<List<String>> parseCsv(String input) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < input.length(); i++) {
      char current = input.charAt(i);
      if (quoted) {
        if (current == '"' && i + 1 < input.length() && input.charAt(i + 1) == '"') {
          cell.append('"');
          i++;
        } else if (current == '"') {
          quoted = false;
        } else {
          cell.append(current);
        }
      } else if (current == '"') {
        quoted = true;
      } else if (current == ',') {
        row.add(cell.toString());
        cell.setLength(0);
      } else if (current == '\n') {
        row.add(cell.toString().replaceAll("\\r$", ""));
        rows.add(row);
        row = new ArrayList<>();
        cell.setLength(0);
      } else {
        cell.append(current);
      }
    }
    if (!cell.isEmpty() || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    return rows;
  }

  private record LocalSeedEvent(
      String id,
      String title,
      String venue,
      String address,
      String startAt,
      String endAt,
      String entryStartAt,
      String priceAmount,
      String priceCurrency,
      String sourceUrl,
      String imageLocal,
      String imageSourceUrl,
      String latitude,
      String longitude,
      List<String> tags) {
    static LocalSeedEvent from(List<String> row) {
      return new LocalSeedEvent(
          row.get(0),
          row.get(1),
          row.get(2),
          row.get(3),
          row.get(4),
          row.get(5),
          row.get(6),
          row.get(7),
          row.get(8),
          row.get(9),
          row.get(10),
          row.get(11),
          row.get(12),
          row.get(13),
          List.of(row.get(14).split(",")).stream().map(String::trim).filter(tag -> !tag.isBlank()).toList());
    }
  }
}
