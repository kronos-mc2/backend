package hr.kronos.backend.storage;

public interface ObjectStorageService {
  boolean isConfigured();

  StoredObject putImage(String key, byte[] bytes, String contentType, int width, int height);

  StoredObject putObject(String key, byte[] bytes, String contentType);

  StoredObjectContent get(String bucketName, String storageKey);

  void delete(String bucketName, String storageKey);
}
