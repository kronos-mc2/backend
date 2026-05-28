package hr.kronos.backend.events;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class EventTagCatalog {
  static final int MAX_TAGS_PER_CATEGORY = 2;

  private static final Map<String, List<String>> CATEGORIES = buildCategories();

  private static final Map<String, TagDefinition> TAGS_BY_NORMALIZED_VALUE = buildTagLookup();

  private EventTagCatalog() {}

  static Optional<TagDefinition> find(String rawTag) {
    String normalized = normalize(rawTag);
    if (normalized == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(TAGS_BY_NORMALIZED_VALUE.get(normalized));
  }

  private static Map<String, TagDefinition> buildTagLookup() {
    Map<String, TagDefinition> lookup = new LinkedHashMap<>();
    CATEGORIES.forEach(
        (category, tags) -> {
          for (String tag : tags) {
            lookup.putIfAbsent(normalize(tag), new TagDefinition(tag, category));
          }
        });
    return lookup;
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.replaceFirst("^#+", "").replace("'", "’").trim().replaceAll("\\s+", " ");
    return normalized.isBlank() ? null : normalized.toLowerCase(Locale.ROOT);
  }

  private static Map<String, List<String>> buildCategories() {
    Map<String, List<String>> categories = new LinkedHashMap<>();
    categories.put(
        "musicGenres",
        List.of(
            "Rock", "Pop", "Jazz", "Blues", "Hip-Hop", "Rap", "Trap", "R&B", "Soul", "Funk",
            "Disco", "House", "Techno", "Trance", "Drum & Bass", "Dubstep", "EDM", "Punk", "Metal",
            "Hardcore", "Indie", "Alternative", "Reggae", "Ska", "Folk", "Acoustic", "Classical",
            "Opera", "Tamburica", "Balkan", "Ex-Yu", "Latin", "Afrobeat", "K-Pop", "J-Pop"));
    categories.put(
        "vibe",
        List.of(
            "Chill", "Cozy", "Relaxed", "Energetic", "Party", "Underground", "Classy", "Romantic",
            "Dark", "Loud", "Intimate", "Friendly", "Wild", "Nostalgic", "Retro", "Experimental",
            "Artsy", "Elegant", "Casual", "Hardcore", "Groovy", "Emotional", "Feel-good",
            "Family-friendly"));
    categories.put(
        "eventType",
        List.of(
            "Concert", "Festival", "Club Night", "DJ Set", "Live Music", "Open Mic", "Karaoke",
            "Stand-up", "Theatre", "Movie Night", "Exhibition", "Workshop", "Lecture", "Meetup",
            "Networking", "Pub Quiz", "Game Night", "Tournament", "Market", "Fair", "Food Event",
            "Tasting", "Charity", "Sports", "Outdoor", "Indoor", "Afterparty", "Student Event"));
    categories.put(
        "audience",
        List.of(
            "Students", "Families", "Kids", "Couples", "Singles", "Gamers", "Artists", "Developers",
            "Entrepreneurs", "LGBTQ+", "Pet-friendly", "International", "Locals", "Beginners",
            "Professionals", "Creatives"));
    categories.put(
        "timeContext",
        List.of(
            "Weekend", "Weekday", "Tonight", "Late Night", "Morning", "Afternoon", "Sunset", "Summer",
            "Winter", "Holiday", "New Year", "Halloween", "Christmas", "Valentine’s", "Free Entry",
            "Tickets Required", "Reservation Needed"));
    return categories;
  }

  record TagDefinition(String tag, String categoryId) {}
}
