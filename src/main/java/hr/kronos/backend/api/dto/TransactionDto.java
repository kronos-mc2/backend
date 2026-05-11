package hr.kronos.backend.api.dto;

import java.math.BigDecimal;

public record TransactionDto(
    String id,
    String eventId,
    String eventTitle,
    String orderId,
    String type,
    BigDecimal amount,
    String currency,
    String status,
    String description,
    String paymentProvider,
    String providerReference,
    String createdAt) {}
