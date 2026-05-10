package hr.kronos.backend.api.dto;

import java.math.BigDecimal;

public record TransactionDto(
    String id,
    String eventId,
    String eventTitle,
    String type,
    BigDecimal amount,
    String currency,
    String status,
    String description,
    String createdAt) {}
