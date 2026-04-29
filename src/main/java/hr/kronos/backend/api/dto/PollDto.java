package hr.kronos.backend.api.dto;

import java.util.List;

public record PollDto(
    String id,
    String question,
    boolean allowMultiple,
    int totalVotes,
    boolean closed,
    List<String> myOptionIds,
    List<PollOptionDto> options) {}
