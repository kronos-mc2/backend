package hr.kronos.backend.storage;

public record StoredObjectContent(byte[] bytes, String contentType, long contentLength) {}
