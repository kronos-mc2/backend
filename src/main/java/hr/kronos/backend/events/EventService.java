package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.CreateFeedPreferenceRequest;
import hr.kronos.backend.api.dto.EventCacheStateDto;
import hr.kronos.backend.api.dto.EventMediaRequest;
import hr.kronos.backend.api.dto.EventParticipantDto;
import hr.kronos.backend.api.dto.EventRatingRequest;
import hr.kronos.backend.api.dto.FeedImpressionRequest;
import hr.kronos.backend.api.dto.FeedPageDto;
import hr.kronos.backend.api.dto.FeedPreferenceDto;
import hr.kronos.backend.api.dto.OrganizerRatingRequest;
import hr.kronos.backend.api.dto.SavedEventsOverviewDto;
import hr.kronos.backend.api.dto.UpdateEventRequest;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.storage.StoredObjectContent;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@SuppressWarnings("java:S107")
public class EventService {
  private final EventQueryService queries;
  private final EventCommandService commands;
  private final EventParticipantService participants;
  private final EventMediaService media;
  private final EventEngagementService engagement;

  public EventService(
      EventQueryService queries,
      EventCommandService commands,
      EventParticipantService participants,
      EventMediaService media,
      EventEngagementService engagement) {
    this.queries = queries;
    this.commands = commands;
    this.participants = participants;
    this.media = media;
    this.engagement = engagement;
  }

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
    return queries.getEvents(from, to, lat, lng, radiusKm, query, tags, attendanceModes, userId);
  }

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
    return queries.getFeed(
        userId, cursor, limit, seed, excludeEventIds, from, to, lat, lng, radiusKm, query, tags, attendanceModes, scope, sort);
  }

  public void recordFeedImpression(FeedImpressionRequest request, String userId) {
    queries.recordFeedImpression(request, userId);
  }

  public AppEventDto getEventById(String eventId, String userId) {
    return queries.getEventById(eventId, userId);
  }

  public EventCacheStateDto getEventCacheState(String eventId, String userId) {
    return queries.getEventCacheState(eventId, userId);
  }

  public List<AppEventDto> getMyEvents(String userId, String filter) {
    return queries.getMyEvents(userId, filter);
  }

  public List<AppEventDto> getLikedEvents(String userId) {
    return queries.getLikedEvents(userId);
  }

  public SavedEventsOverviewDto getSavedEventsOverview(String userId) {
    return queries.getSavedEventsOverview(userId);
  }

  public List<FeedPreferenceDto> getFeedPreferences(String userId) {
    return queries.getFeedPreferences(userId);
  }

  public FeedPreferenceDto createFeedPreference(CreateFeedPreferenceRequest request, String userId) {
    return queries.createFeedPreference(request, userId);
  }

  public void deleteFeedPreference(String preferenceId, String userId) {
    queries.deleteFeedPreference(preferenceId, userId);
  }

  public List<AppEventDto> getUpcomingCreatedByUser(String targetUserId, String userId) {
    return queries.getUpcomingCreatedByUser(targetUserId, userId);
  }

  public AppEventDto createEvent(CreateEventRequest request, String creatorUserId) {
    return commands.createEvent(request, creatorUserId);
  }

  public AppEventDto createEventWithImages(
      CreateEventRequest request,
      List<MultipartFile> images,
      MultipartFile video,
      String creatorUserId) {
    return commands.createEventWithImages(request, images, video, creatorUserId);
  }

  public AppEventDto updateEvent(String eventId, UpdateEventRequest request, String userId) {
    return commands.updateEvent(eventId, request, userId);
  }

  public void deleteEvent(String eventId, String userId) {
    commands.deleteEvent(eventId, userId);
  }

  public AppEventDto joinEvent(String eventId, String userId) {
    return participants.joinEvent(eventId, userId);
  }

  public AppEventDto joinEventAfterPayment(String eventId, String userId) {
    return participants.joinEventAfterPayment(eventId, userId);
  }

  public AppEventDto leaveEvent(String eventId, String userId) {
    return participants.leaveEvent(eventId, userId);
  }

  public List<EventParticipantDto> getParticipants(String eventId, String userId) {
    return participants.getParticipants(eventId, userId);
  }

  public List<EventParticipantDto> approveParticipant(String eventId, String participantUserId, String userId) {
    return participants.approveParticipant(eventId, participantUserId, userId);
  }

  public List<EventParticipantDto> removeParticipant(String eventId, String participantUserId, String userId) {
    return participants.removeParticipant(eventId, participantUserId, userId);
  }

  public List<EventParticipantDto> blockParticipant(String eventId, String participantUserId, String userId) {
    return participants.blockParticipant(eventId, participantUserId, userId);
  }

  public AppEventDto addMedia(String eventId, EventMediaRequest request, String userId) {
    return media.addMedia(eventId, request, userId);
  }

  public StoredObjectContent getMediaContent(String eventId, String mediaId, String userId) {
    return media.getMediaContent(eventId, mediaId, userId);
  }

  public StoredObjectContent getMediaThumbnailContent(String eventId, String mediaId, String userId) {
    return media.getMediaThumbnailContent(eventId, mediaId, userId);
  }

  public AppEventDto uploadMedia(String eventId, MultipartFile image, MultipartFile video, String userId) {
    return media.uploadMedia(eventId, image, video, userId);
  }

  public AppEventDto uploadVideoBytes(String eventId, byte[] bytes, String contentType, String filename, String userId) {
    return media.uploadVideoBytes(eventId, bytes, contentType, filename, userId);
  }

  public AppEventDto deleteMedia(String eventId, String mediaId, String userId) {
    return media.deleteMedia(eventId, mediaId, userId);
  }

  public AppEventDto likeEvent(String eventId, String userId) {
    return engagement.likeEvent(eventId, userId);
  }

  public AppEventDto unlikeEvent(String eventId, String userId) {
    return engagement.unlikeEvent(eventId, userId);
  }

  public AppEventDto rateOrganizer(String eventId, OrganizerRatingRequest request, String userId) {
    return engagement.rateOrganizer(eventId, request, userId);
  }

  public AppEventDto rateEvent(String eventId, EventRatingRequest request, String userId) {
    return engagement.rateEvent(eventId, request, userId);
  }

  public AppEventDto toDto(EventRow row) {
    return queries.toDto(row);
  }
}
