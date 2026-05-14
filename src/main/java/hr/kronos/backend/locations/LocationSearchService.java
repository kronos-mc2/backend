package hr.kronos.backend.locations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hr.kronos.backend.api.dto.CoordinatesDto;
import hr.kronos.backend.api.dto.LocationSearchResultDto;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LocationSearchService {
  private static final int DEFAULT_LIMIT = 6;
  private static final int MAX_LIMIT = 10;
  private static final int MIN_QUERY_LENGTH = 2;
  private static final int MAX_QUERY_LENGTH = 160;
  private static final int MAX_CACHE_ENTRIES = 80;
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final double PROXIMITY_VIEWBOX_DELTA = 0.35;

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final String nominatimBaseUrl;
  private final String userAgent;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public LocationSearchService(
      ObjectMapper objectMapper,
      @Value("${app.location-search.nominatim.base-url}") String nominatimBaseUrl,
      @Value("${app.location-search.user-agent}") String userAgent) {
    this.objectMapper = objectMapper;
    this.nominatimBaseUrl = nominatimBaseUrl.replaceAll("/+$", "");
    this.userAgent = userAgent;
    this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
  }

  public List<LocationSearchResultDto> search(
      String query, String locale, Integer requestedLimit, Double proximityLat, Double proximityLng) {
    String normalizedQuery = normalizeQuery(query);
    if (normalizedQuery.length() < MIN_QUERY_LENGTH) {
      return List.of();
    }

    int limit = normalizeLimit(requestedLimit);
    String normalizedLocale = normalizeLocale(locale);
    String cacheKey = cacheKey(normalizedQuery, normalizedLocale, limit, proximityLat, proximityLng);
    CacheEntry cached = cache.get(cacheKey);

    if (cached != null && cached.isFresh()) {
      return cached.results();
    }

    List<LocationSearchResultDto> results =
        fetchNominatim(normalizedQuery, normalizedLocale, limit, proximityLat, proximityLng);
    cache.put(cacheKey, new CacheEntry(Instant.now(), results));
    pruneCache();

    return results;
  }

  private List<LocationSearchResultDto> fetchNominatim(
      String query, String locale, int limit, Double proximityLat, Double proximityLng) {
    boolean hasProximity = isValidCoordinate(proximityLat, proximityLng);
    URI uri =
        buildNominatimUri(
            query, locale, limit, hasProximity ? proximityLat : null, hasProximity ? proximityLng : null);
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("User-Agent", userAgent)
            .GET()
            .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Location provider failed");
      }

      JsonNode payload = objectMapper.readTree(response.body());
      if (!payload.isArray()) {
        return List.of();
      }

      List<LocationSearchResultDto> results = new ArrayList<>();
      for (JsonNode item : payload) {
        LocationSearchResultDto result = toLocationSearchResult(item);
        if (result != null) {
          results.add(result);
        }
      }

      return dedupeResults(results);
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Location provider failed", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Location provider interrupted", exception);
    }
  }

  private URI buildNominatimUri(
      String query, String locale, int limit, Double proximityLat, Double proximityLng) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(nominatimBaseUrl)
            .path("/search")
            .queryParam("q", query)
            .queryParam("format", "jsonv2")
            .queryParam("addressdetails", "1")
            .queryParam("dedupe", "1")
            .queryParam("limit", limit)
            .queryParam("accept-language", locale);

    if (proximityLat != null && proximityLng != null) {
      double minLng = Math.max(-180, proximityLng - PROXIMITY_VIEWBOX_DELTA);
      double minLat = Math.max(-90, proximityLat - PROXIMITY_VIEWBOX_DELTA);
      double maxLng = Math.min(180, proximityLng + PROXIMITY_VIEWBOX_DELTA);
      double maxLat = Math.min(90, proximityLat + PROXIMITY_VIEWBOX_DELTA);
      builder.queryParam("viewbox", String.format(Locale.ROOT, "%f,%f,%f,%f", minLng, minLat, maxLng, maxLat));
      builder.queryParam("bounded", 0);
    }

    return builder.build().encode().toUri();
  }

  private LocationSearchResultDto toLocationSearchResult(JsonNode item) {
    String id = textValue(item, "place_id");
    String displayName = textValue(item, "display_name");
    BigDecimal latitude = decimalValue(item, "lat");
    BigDecimal longitude = decimalValue(item, "lon");

    if (id == null || displayName == null || latitude == null || longitude == null) {
      return null;
    }

    DisplayName display = composeAddressDisplay(item, displayName);
    return new LocationSearchResultDto(
        id,
        display.title(),
        display.subtitle(),
        new CoordinatesDto(latitude.doubleValue(), longitude.doubleValue()),
        "nominatim");
  }

  private String normalizeQuery(String query) {
    if (query == null) {
      return "";
    }

    String normalized = query.trim().replaceAll("\\s+", " ");
    return normalized.length() > MAX_QUERY_LENGTH ? normalized.substring(0, MAX_QUERY_LENGTH) : normalized;
  }

  private int normalizeLimit(Integer requestedLimit) {
    if (requestedLimit == null) {
      return DEFAULT_LIMIT;
    }

    return Math.max(1, Math.min(MAX_LIMIT, requestedLimit));
  }

  private String normalizeLocale(String locale) {
    if (locale == null || locale.isBlank()) {
      return "hr";
    }

    return locale.trim().toLowerCase(Locale.ROOT);
  }

  private String cacheKey(String query, String locale, int limit, Double proximityLat, Double proximityLng) {
    String proximity =
        isValidCoordinate(proximityLat, proximityLng)
            ? String.format(Locale.ROOT, "%.3f:%.3f", proximityLat, proximityLng)
            : "none";
    return "%s:%d:%s:%s".formatted(locale, limit, proximity, query.toLowerCase(Locale.ROOT));
  }

  private boolean isValidCoordinate(Double latitude, Double longitude) {
    return latitude != null
        && longitude != null
        && Double.isFinite(latitude)
        && Double.isFinite(longitude)
        && Math.abs(latitude) <= 90
        && Math.abs(longitude) <= 180;
  }

  private void pruneCache() {
    if (cache.size() <= MAX_CACHE_ENTRIES) {
      return;
    }

    int deleteCount = cache.size() - MAX_CACHE_ENTRIES;
    cache.entrySet().stream()
        .sorted(Comparator.comparing((entry) -> entry.getValue().createdAt()))
        .limit(deleteCount)
        .map(Map.Entry::getKey)
        .toList()
        .forEach(cache::remove);
  }

  private String textValue(JsonNode item, String fieldName) {
    JsonNode value = item.get(fieldName);
    if (value == null || value.isNull()) {
      return null;
    }

    String text = value.asText();
    return text.isBlank() ? null : text;
  }

  private String addressValue(JsonNode item, String... fieldNames) {
    JsonNode address = item.get("address");
    if (address == null || address.isNull()) {
      return null;
    }

    for (String fieldName : fieldNames) {
      String value = textValue(address, fieldName);
      if (value != null) {
        return value;
      }
    }

    return null;
  }

  private BigDecimal decimalValue(JsonNode item, String fieldName) {
    String text = textValue(item, fieldName);
    if (text == null) {
      return null;
    }

    try {
      return new BigDecimal(text);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private DisplayName composeAddressDisplay(JsonNode item, String displayName) {
    String road = addressValue(item, "road", "pedestrian", "footway", "residential", "path");
    if (road == null) {
      return parseDisplayName(displayName);
    }

    String houseNumber = addressValue(item, "house_number");
    String title = houseNumber == null ? road : road + " " + houseNumber;
    List<String> subtitleParts = new ArrayList<>();

    addUnique(subtitleParts, addressValue(item, "city", "town", "village", "municipality", "suburb", "hamlet"));
    addUnique(subtitleParts, addressValue(item, "county", "state_district"));
    addUnique(subtitleParts, addressValue(item, "state"));
    addUnique(subtitleParts, addressValue(item, "postcode"));
    addUnique(subtitleParts, addressValue(item, "country"));

    return new DisplayName(title, String.join(", ", subtitleParts));
  }

  private DisplayName parseDisplayName(String value) {
    String[] parts = value.split(",");
    String title = parts.length > 0 ? parts[0].trim() : value;
    List<String> subtitleParts = new ArrayList<>();

    for (int index = 1; index < parts.length; index += 1) {
      String part = parts[index].trim();
      if (!part.isBlank()) {
        subtitleParts.add(part);
      }
    }

    return new DisplayName(title, String.join(", ", subtitleParts));
  }

  private List<LocationSearchResultDto> dedupeResults(List<LocationSearchResultDto> results) {
    List<LocationSearchResultDto> deduped = new ArrayList<>();
    List<String> seen = new ArrayList<>();

    for (LocationSearchResultDto result : results) {
      String key = dedupeKey(result);
      if (!seen.contains(key)) {
        seen.add(key);
        deduped.add(result);
      }
    }

    return deduped;
  }

  private String dedupeKey(LocationSearchResultDto result) {
    String[] subtitleParts = result.subtitle() == null ? new String[0] : result.subtitle().split(",");
    List<String> normalizedSubtitleParts = new ArrayList<>();

    for (int index = 0; index < subtitleParts.length && index < 2; index += 1) {
      String part = normalizeDedupeText(subtitleParts[index]);
      if (!part.isBlank()) {
        normalizedSubtitleParts.add(part);
      }
    }

    return normalizeDedupeText(result.title()) + ":" + String.join(",", normalizedSubtitleParts);
  }

  private String normalizeDedupeText(String value) {
    if (value == null) {
      return "";
    }

    return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private void addUnique(List<String> values, String value) {
    if (value == null || values.contains(value)) {
      return;
    }

    values.add(value);
  }

  private record CacheEntry(Instant createdAt, List<LocationSearchResultDto> results) {
    boolean isFresh() {
      return Instant.now().isBefore(createdAt.plus(CACHE_TTL));
    }
  }

  private record DisplayName(String title, String subtitle) {}
}
