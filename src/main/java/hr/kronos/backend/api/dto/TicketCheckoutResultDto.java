package hr.kronos.backend.api.dto;

public record TicketCheckoutResultDto(TicketCheckoutDto checkout, AppEventDto event, TransactionDto transaction) {}
