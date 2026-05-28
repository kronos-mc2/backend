package hr.kronos.backend.api.dto;

import java.util.List;

public record SavedEventsOverviewDto(
    List<AppEventDto> savedEvents,
    List<AppEventDto> goingSoon,
    List<AppEventDto> pastEvents) {}
