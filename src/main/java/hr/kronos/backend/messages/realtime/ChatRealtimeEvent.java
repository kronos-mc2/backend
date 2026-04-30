package hr.kronos.backend.messages.realtime;

public record ChatRealtimeEvent(String type, String roomId, Object payload) {}
