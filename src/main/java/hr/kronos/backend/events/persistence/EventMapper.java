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
      @Param("cursorStartAt") OffsetDateTime cursorStartAt,
      @Param("cursorId") String cursorId,
      @Param("limit") int limit);

  EventRow findById(@Param("id") String id, @Param("userId") String userId);

  EventRow findAccessibleById(@Param("id") String id, @Param("userId") String userId);

  List<EventRow> findByUser(@Param("userId") String userId, @Param("filter") String filter);

  List<EventRow> findLikedByUser(@Param("userId") String userId);

  List<EventMediaRow> findMediaByEventId(@Param("eventId") String eventId);

  List<EventMediaRow> findMediaByEventIds(@Param("eventIds") List<String> eventIds);

  long countAllEvents();

  long countPublicEvents();

  int insert(EventRow event);

  int insertMedia(EventMediaRow media);

  int insertLike(@Param("eventId") String eventId, @Param("userId") String userId);

  int deleteLike(@Param("eventId") String eventId, @Param("userId") String userId);

  int upsertParticipant(@Param("eventId") String eventId, @Param("userId") String userId, @Param("status") String status);

  int incrementParticipantCount(@Param("eventId") String eventId);

  int decrementParticipantCount(@Param("eventId") String eventId);

  int upsertOrganizerRating(
      @Param("eventId") String eventId,
      @Param("organizerUserId") String organizerUserId,
      @Param("raterUserId") String raterUserId,
      @Param("rating") int rating,
      @Param("comment") String comment);

  int refreshOrganizerRatingAggregate(@Param("eventId") String eventId);
}
