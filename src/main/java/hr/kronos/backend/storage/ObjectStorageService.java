package hr.kronos.backend.storage;

public interface ObjectStorageService {
  boolean isConfigured();

  StoredObject putImage(String key, byte[] bytes, String contentType, int width, int height);

  void delete(String bucketName, String storageKey);
}
