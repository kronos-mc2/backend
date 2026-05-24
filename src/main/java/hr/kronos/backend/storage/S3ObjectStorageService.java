package hr.kronos.backend.storage;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class S3ObjectStorageService implements ObjectStorageService {
  private final ObjectStorageProperties properties;
  private S3Client client;

  public S3ObjectStorageService(ObjectStorageProperties properties) {
    this.properties = properties;
  }

  @Override
  public boolean isConfigured() {
    return hasText(properties.getBucket())
        && hasText(properties.getAccessKey())
        && hasText(properties.getSecretKey());
  }

  @Override
  public StoredObject putImage(String key, byte[] bytes, String contentType, int width, int height) {
    ensureConfigured();
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(properties.getBucket().trim())
        .key(key)
        .contentType(contentType)
        .contentLength((long) bytes.length)
        .build();
    getClient().putObject(request, RequestBody.fromBytes(bytes));
    return new StoredObject(
        publicUrl(key),
        properties.getBucket().trim(),
        key,
        contentType,
        bytes.length,
        width,
        height);
  }

  @Override
  public StoredObjectContent get(String bucketName, String storageKey) {
    ensureConfigured();
    if (!hasText(bucketName) || !hasText(storageKey)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found.");
    }

    try {
      var responseBytes = getClient().getObjectAsBytes(
          GetObjectRequest.builder()
              .bucket(bucketName.trim())
              .key(storageKey.trim())
              .build());
      GetObjectResponse response = responseBytes.response();
      return new StoredObjectContent(
          responseBytes.asByteArray(),
          response.contentType(),
          response.contentLength() == null ? responseBytes.asByteArray().length : response.contentLength());
    } catch (S3Exception exception) {
      if (exception.statusCode() == HttpStatus.NOT_FOUND.value()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found.", exception);
      }
      throw exception;
    }
  }

  @Override
  public void delete(String bucketName, String storageKey) {
    if (!hasText(bucketName) || !hasText(storageKey) || !isConfigured()) {
      return;
    }
    getClient().deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(storageKey).build());
  }

  private S3Client getClient() {
    if (client == null) {
      S3ClientBuilder builder = S3Client.builder()
          .region(Region.of(hasText(properties.getRegion()) ? properties.getRegion().trim() : "auto"))
          .credentialsProvider(StaticCredentialsProvider.create(
              AwsBasicCredentials.create(properties.getAccessKey().trim(), properties.getSecretKey().trim())))
          .forcePathStyle(properties.isPathStyleAccess());
      if (hasText(properties.getEndpoint())) {
        builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
      }
      client = builder.build();
    }
    return client;
  }

  private void ensureConfigured() {
    if (!isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Object storage is not configured. Check APP_STORAGE_BUCKET, APP_STORAGE_ACCESS_KEY and APP_STORAGE_SECRET_KEY.");
    }
  }

  private String publicUrl(String key) {
    String publicBaseUrl = trimTrailingSlash(properties.getPublicBaseUrl());
    if (hasText(publicBaseUrl)) {
      return publicBaseUrl + "/" + key;
    }
    String endpoint = trimTrailingSlash(properties.getEndpoint());
    if (hasText(endpoint)) {
      return endpoint + "/" + properties.getBucket().trim() + "/" + key;
    }
    return "https://" + properties.getBucket().trim() + ".s3." + properties.getRegion().trim() + ".amazonaws.com/" + key;
  }

  private static boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static String trimTrailingSlash(String value) {
    if (!hasText(value)) {
      return null;
    }
    return value.trim().replaceAll("/+$", "");
  }
}
