package hr.kronos.backend.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class ObjectStorageProperties {
  private String provider = "s3";
  private String endpoint;
  private String region = "auto";
  private String bucket = "gik-event-media";
  private String publicBaseUrl;
  private String accessKey;
  private String secretKey;
  private boolean pathStyleAccess = true;
  private long maxTotalBytes = 10L * 1024L * 1024L * 1024L;
  private long maxFileBytes = 5L * 1024L * 1024L;
  private int minImageWidth = 640;
  private int minImageHeight = 640;
  private int maxImageWidth = 8000;
  private int maxImageHeight = 8000;

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getPublicBaseUrl() {
    return publicBaseUrl;
  }

  public void setPublicBaseUrl(String publicBaseUrl) {
    this.publicBaseUrl = publicBaseUrl;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public boolean isPathStyleAccess() {
    return pathStyleAccess;
  }

  public void setPathStyleAccess(boolean pathStyleAccess) {
    this.pathStyleAccess = pathStyleAccess;
  }

  public long getMaxTotalBytes() {
    return maxTotalBytes;
  }

  public void setMaxTotalBytes(long maxTotalBytes) {
    this.maxTotalBytes = maxTotalBytes;
  }

  public long getMaxFileBytes() {
    return maxFileBytes;
  }

  public void setMaxFileBytes(long maxFileBytes) {
    this.maxFileBytes = maxFileBytes;
  }

  public int getMinImageWidth() {
    return minImageWidth;
  }

  public void setMinImageWidth(int minImageWidth) {
    this.minImageWidth = minImageWidth;
  }

  public int getMinImageHeight() {
    return minImageHeight;
  }

  public void setMinImageHeight(int minImageHeight) {
    this.minImageHeight = minImageHeight;
  }

  public int getMaxImageWidth() {
    return maxImageWidth;
  }

  public void setMaxImageWidth(int maxImageWidth) {
    this.maxImageWidth = maxImageWidth;
  }

  public int getMaxImageHeight() {
    return maxImageHeight;
  }

  public void setMaxImageHeight(int maxImageHeight) {
    this.maxImageHeight = maxImageHeight;
  }
}
