package hr.kronos.backend.events.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EventMapper {
  List<EventRow> findAll();

  List<EventRow> findFeed();

  long countAllEvents();

  long countPublicEvents();

  int insert(EventRow event);

  int upsertParticipant(@Param("eventId") String eventId, @Param("userId") String userId, @Param("status") String status);
}
