package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CreateFeedPreferenceRequest;
import hr.kronos.backend.api.dto.EventCacheStateDto;
import hr.kronos.backend.api.dto.FeedImpressionRequest;
import hr.kronos.backend.api.dto.FeedPageDto;
import hr.kronos.backend.api.dto.FeedPreferenceDto;
import hr.kronos.backend.api.dto.SavedEventsOverviewDto;
import hr.kronos.backend.events.persistence.EventRow;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@SuppressWarnings("java:S107")
public class EventQueryService {
  private final EventDomainService events;

  public EventQueryService(EventDomainService events) {
    this.events = events;
  }

  @Transactional
  public List<AppEventDto> getEvents(
      String from,
      String to,
      Double lat,
      Double lng,
      Double radiusKm,
      String query,
      String tags,
      String attendanceModes,
      String userId) {
    return events.getEvents(from, to, lat, lng, radiusKm, query, tags, attendanceModes, userId);
  }

  @Transactional
  public FeedPageDto getFeed(
      String userId,
      String cursor,
      Integer limit,
      String seed,
      String excludeEventIds,
      String from,
      String to,
      Double lat,
      Double lng,
      Double radiusKm,
      String query,
      String tags,
      String attendanceModes,
      String scope,
      String sort) {
    return events.getFeed(
        userId,
        cursor,
        limit,
        seed,
        excludeEventIds,
        from,
        to,
        lat,
        lng,
        radiusKm,
        query,
        tags,
        attendanceModes,
        scope,
        sort);
  }

  @Transactional
  public void recordFeedImpression(FeedImpressionRequest request, String userId) {
    events.recordFeedImpression(request, userId);
  }

  @Transactional
  public AppEventDto getEventById(String eventId, String userId) {
    return events.getEventById(eventId, userId);
  }

  @Transactional
  public EventCacheStateDto getEventCacheState(String eventId, String userId) {
    return events.getEventCacheState(eventId, userId);
  }

  @Transactional
  public List<AppEventDto> getMyEvents(String userId, String filter) {
    return events.getMyEvents(userId, filter);
  }

  @Transactional(readOnly = true)
  public List<AppEventDto> getLikedEvents(String userId) {
    return events.getLikedEvents(userId);
  }

  @Transactional
  public SavedEventsOverviewDto getSavedEventsOverview(String userId) {
    return events.getSavedEventsOverview(userId);
  }

  @Transactional(readOnly = true)
  public List<FeedPreferenceDto> getFeedPreferences(String userId) {
    return events.getFeedPreferences(userId);
  }

  @Transactional
  public FeedPreferenceDto createFeedPreference(CreateFeedPreferenceRequest request, String userId) {
    return events.createFeedPreference(request, userId);
  }

  @Transactional
  public void deleteFeedPreference(String preferenceId, String userId) {
    events.deleteFeedPreference(preferenceId, userId);
  }

  @Transactional(readOnly = true)
  public List<AppEventDto> getUpcomingCreatedByUser(String targetUserId, String userId) {
    return events.getUpcomingCreatedByUser(targetUserId, userId);
  }

  @Transactional(readOnly = true)
  public AppEventDto toDto(EventRow row) {
    return events.toDto(row);
  }
}
