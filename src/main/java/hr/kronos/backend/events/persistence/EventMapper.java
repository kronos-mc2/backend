package hr.kronos.backend.events.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventMapper {
  List<EventRow> findAll();

  List<EventRow> findFeed();

  long countAllEvents();

  long countPublicEvents();

  int insert(EventRow event);
}
