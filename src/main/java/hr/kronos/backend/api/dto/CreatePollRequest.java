package hr.kronos.backend.api.dto;

import java.util.List;

public record CreatePollRequest(String question, List<String> options, Boolean allowMultiple) {}
