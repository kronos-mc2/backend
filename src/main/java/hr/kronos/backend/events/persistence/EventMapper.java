package hr.kronos.backend.events.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventMapper {
  List<EventRow> findAll(
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to,
      @Param("lat") Double lat,
      @Param("lng") Double lng,
      @Param("radiusKm") Double radiusKm,
      @Param("query") String query,
      @Param("userId") String userId);

  List<EventRow> findFeedPage(
      @Param("userId") String userId,
      @Param("seed") String seed,
      @Param("offset") int offset,
      @Param("limit") int limit);

  EventRow findById(@Param("id") String id, @Param("userId") String userId);

  EventRow findAccessibleById(@Param("id") String id, @Param("userId") String userId);

  List<EventRow> findByUser(@Param("userId") String userId, @Param("filter") String filter);

  List<EventRow> findLikedByUser(@Param("userId") String userId);

  List<EventRow> findUpcomingCreatedByUser(@Param("targetUserId") String targetUserId, @Param("userId") String userId);

  List<EventMediaRow> findMediaByEventId(@Param("eventId") String eventId);

  List<EventMediaRow> findMediaByEventIds(@Param("eventIds") List<String> eventIds);

  EventMediaRow findMediaById(@Param("eventId") String eventId, @Param("mediaId") String mediaId);

  long sumStoredMediaBytes();

  List<FeedPreferenceRow> findFeedPreferences(@Param("userId") String userId);

  List<EventParticipantRow> findParticipantsByEventId(@Param("eventId") String eventId);

  int countWaitlistedParticipants(@Param("eventId") String eventId);

  long countAllEvents();

  long countPublicEvents();

  int insert(EventRow event);

  int insertMedia(EventMediaRow media);

  int insertTag(@Param("eventId") String eventId, @Param("tag") String tag);

  int deleteTags(@Param("eventId") String eventId);

  int insertFeedPreference(
      @Param("id") String id,
      @Param("userId") String userId,
      @Param("blockType") String blockType,
      @Param("targetId") String targetId,
      @Param("targetLabel") String targetLabel);

  int deleteFeedPreference(@Param("preferenceId") String preferenceId, @Param("userId") String userId);

  int recordFeedImpression(@Param("eventId") String eventId, @Param("userId") String userId);

  int markFeedInteraction(@Param("eventId") String eventId, @Param("userId") String userId);

  int update(EventRow event);

  int delete(@Param("eventId") String eventId);

  int deleteMedia(@Param("eventId") String eventId, @Param("mediaId") String mediaId);

  int insertLike(@Param("eventId") String eventId, @Param("userId") String userId);

  int deleteLike(@Param("eventId") String eventId, @Param("userId") String userId);

  int upsertParticipant(@Param("eventId") String eventId, @Param("userId") String userId, @Param("status") String status);

  int incrementParticipantCount(@Param("eventId") String eventId);

  int decrementParticipantCount(@Param("eventId") String eventId);

  int updateParticipantStatus(@Param("eventId") String eventId, @Param("userId") String userId, @Param("status") String status);

  boolean isUserBlocked(@Param("eventId") String eventId, @Param("userId") String userId);

  int blockUser(@Param("eventId") String eventId, @Param("userId") String userId, @Param("createdByUserId") String createdByUserId);

  int unblockUser(@Param("eventId") String eventId, @Param("userId") String userId);

  int upsertOrganizerRating(
      @Param("eventId") String eventId,
      @Param("organizerUserId") String organizerUserId,
      @Param("raterUserId") String raterUserId,
      @Param("rating") int rating,
      @Param("comment") String comment);

  int refreshOrganizerRatingAggregate(@Param("eventId") String eventId);

  int upsertEventRating(
      @Param("eventId") String eventId,
      @Param("raterUserId") String raterUserId,
      @Param("rating") int rating,
      @Param("comment") String comment);

  int refreshEventRatingAggregate(@Param("eventId") String eventId);

  int insertNotification(
      @Param("id") String id,
      @Param("userId") String userId,
      @Param("notificationType") String notificationType,
      @Param("title") String title,
      @Param("body") String body,
      @Param("eventId") String eventId);

  int markPastEventsFinished();
}
