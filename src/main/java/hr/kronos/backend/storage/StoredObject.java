package hr.kronos.backend.storage;

public record StoredObject(
    String url,
    String bucketName,
    String storageKey,
    String contentType,
    long byteSize,
    Integer width,
    Integer height) {}
