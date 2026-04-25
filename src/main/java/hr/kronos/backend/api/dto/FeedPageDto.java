package hr.kronos.backend.api.dto;

import java.util.List;

public record FeedPageDto(List<AppEventDto> items, String nextCursor, boolean hasMore) {}
