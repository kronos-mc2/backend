package hr.kronos.backend.events.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventMapper {
  List<EventRow> findAll(
      @Param("from") java.time.OffsetDateTime from,
      @Param("to") java.time.OffsetDateTime to,
      @Param("lat") Double lat,
      @Param("lng") Double lng,
      @Param("radiusKm") Double radiusKm,
      @Param("query") String query,
      @Param("userId") String userId);

  List<EventRow> findFeed(@Param("userId") String userId);

  EventRow findById(@Param("id") String id, @Param("userId") String userId);

  EventRow findAccessibleById(@Param("id") String id, @Param("userId") String userId);

  List<EventRow> findByUser(@Param("userId") String userId, @Param("filter") String filter);

  List<EventMediaRow> findMediaByEventId(@Param("eventId") String eventId);

  long countAllEvents();

  long countPublicEvents();

  int insert(EventRow event);

  int upsertParticipant(@Param("eventId") String eventId, @Param("userId") String userId, @Param("status") String status);

  int incrementParticipantCount(@Param("eventId") String eventId);

  int decrementParticipantCount(@Param("eventId") String eventId);
}
