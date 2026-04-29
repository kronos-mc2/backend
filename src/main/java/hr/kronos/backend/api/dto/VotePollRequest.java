package hr.kronos.backend.api.dto;

import java.util.List;

public record VotePollRequest(List<String> optionIds) {}
