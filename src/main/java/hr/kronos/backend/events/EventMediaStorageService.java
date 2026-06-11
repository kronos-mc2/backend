package hr.kronos.backend.events;

import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventMediaRow;
import hr.kronos.backend.storage.ObjectStorageProperties;
import hr.kronos.backend.storage.ObjectStorageService;
import hr.kronos.backend.storage.StoredObject;
import hr.kronos.backend.storage.StoredObjectContent;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventMediaStorageService {
  static final String MEDIA_TYPE_IMAGE = "image";
  static final String MEDIA_TYPE_VIDEO = "video";

  private static final String MEDIA_ID_PREFIX = "media-";
  private static final int MAX_EVENT_IMAGES = 5;
  private static final int MAX_EVENT_VIDEOS = 1;
  private static final int THUMBNAIL_MAX_WIDTH = 600;
  private static final int THUMBNAIL_MAX_HEIGHT = 400;
  private static final String CONTENT_TYPE_JPEG = "image/jpeg";
  private static final String CONTENT_TYPE_PNG = "image/png";
  private static final String CONTENT_TYPE_MP4 = "video/mp4";
  private static final String CONTENT_TYPE_QUICKTIME = "video/quicktime";
  private static final String CONTENT_TYPE_M4V = "video/x-m4v";
  private static final String EVENT_IMAGES_LIMIT_MESSAGE = "events accept up to " + MAX_EVENT_IMAGES + " images.";
  private static final String EVENT_VIDEO_LIMIT_MESSAGE = "events accept up to one video.";

  private final EventMapper eventMapper;
  private final ObjectStorageService objectStorageService;
  private final ObjectStorageProperties objectStorageProperties;

  public EventMediaStorageService(
      EventMapper eventMapper,
      ObjectStorageService objectStorageService,
      ObjectStorageProperties objectStorageProperties) {
    this.eventMapper = eventMapper;
    this.objectStorageService = objectStorageService;
    this.objectStorageProperties = objectStorageProperties;
  }

  public void validateUploadedImageCount(List<MultipartFile> images, boolean requireAtLeastOne) {
    int count = images == null ? 0 : images.size();
    if (requireAtLeastOne && count == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one image is required.");
    }
    if (count > MAX_EVENT_IMAGES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, EVENT_IMAGES_LIMIT_MESSAGE);
    }
  }

  public void validateUploadedVideoCount(int count) {
    if (count > MAX_EVENT_VIDEOS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, EVENT_VIDEO_LIMIT_MESSAGE);
    }
  }

  public void ensureMediaCapacity(String eventId, String mediaType, int incomingCount) {
    if (incomingCount <= 0) {
      return;
    }
    int currentCount = countMediaItems(eventMapper.findMediaByEventId(eventId), mediaType);
    int maxCount = MEDIA_TYPE_VIDEO.equals(mediaType) ? MAX_EVENT_VIDEOS : MAX_EVENT_IMAGES;
    if (currentCount + incomingCount > maxCount) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          MEDIA_TYPE_VIDEO.equals(mediaType)
              ? EVENT_VIDEO_LIMIT_MESSAGE
              : EVENT_IMAGES_LIMIT_MESSAGE);
    }
  }

  public EventMediaRow buildUploadedImageMedia(String eventId, MultipartFile file, int sortOrder) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image is required.");
    }
    try {
      byte[] bytes = file.getBytes();
      validateImageFileSize(bytes.length);
      String contentType = normalizeImageContentType(file.getContentType(), file.getOriginalFilename());
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image must be a readable JPEG or PNG file.");
      }
      validateImageDimensions(image.getWidth(), image.getHeight());
      ensureStorageQuota(bytes.length);
      StoredObject storedObject = objectStorageService.putImage(
          buildStorageKey(eventId, MEDIA_TYPE_IMAGE, contentType),
          bytes,
          contentType,
          image.getWidth(),
          image.getHeight());

      EventMediaRow media = new EventMediaRow();
      media.setId(newMediaId());
      media.setEventId(eventId);
      media.setMediaType(MEDIA_TYPE_IMAGE);
      media.setUrl(storedObject.url());
      applyImageThumbnail(media, eventId, image);
      media.setOriginalFilename(normalizeOriginalFilename(file.getOriginalFilename()));
      media.setStorageKey(storedObject.storageKey());
      media.setBucketName(storedObject.bucketName());
      media.setContentType(storedObject.contentType());
      media.setByteSize(storedObject.byteSize());
      media.setWidth(storedObject.width());
      media.setHeight(storedObject.height());
      media.setSortOrder(sortOrder);
      return media;
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image could not be read.", exception);
    }
  }

  public EventMediaRow buildUploadedVideoMedia(String eventId, MultipartFile file, int sortOrder) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video is required.");
    }
    try {
      return buildUploadedVideoMedia(eventId, file.getBytes(), file.getContentType(), file.getOriginalFilename(), sortOrder);
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video could not be read.", exception);
    }
  }

  public EventMediaRow buildUploadedVideoMedia(
      String eventId,
      byte[] bytes,
      String rawContentType,
      String originalFilename,
      int sortOrder) {
    validateVideoFileSize(bytes.length);
    String contentType = normalizeVideoContentType(rawContentType, originalFilename);
    ensureStorageQuota(bytes.length);
    StoredObject storedObject = objectStorageService.putObject(
        buildStorageKey(eventId, MEDIA_TYPE_VIDEO, contentType),
        bytes,
        contentType);

    EventMediaRow media = new EventMediaRow();
    media.setId(newMediaId());
    media.setEventId(eventId);
    media.setMediaType(MEDIA_TYPE_VIDEO);
    media.setUrl(storedObject.url());
    media.setOriginalFilename(normalizeOriginalFilename(originalFilename));
    media.setStorageKey(storedObject.storageKey());
    media.setBucketName(storedObject.bucketName());
    media.setContentType(storedObject.contentType());
    media.setByteSize(storedObject.byteSize());
    media.setSortOrder(sortOrder);
    return media;
  }

  public StoredObjectContent getMediaContent(EventMediaRow media) {
    StoredObjectContent content = objectStorageService.get(resolveBucketName(media), media.getStorageKey());
    String contentType = firstNonBlank(media.getContentType(), firstNonBlank(content.contentType(), defaultContentType(media.getMediaType())));
    return new StoredObjectContent(content.bytes(), contentType, content.contentLength());
  }

  public StoredObjectContent getMediaThumbnailContent(EventMediaRow media) {
    StoredObjectContent content = objectStorageService.get(resolveThumbnailBucketName(media), media.getThumbnailStorageKey());
    String contentType = firstNonBlank(media.getThumbnailContentType(), firstNonBlank(content.contentType(), CONTENT_TYPE_JPEG));
    return new StoredObjectContent(content.bytes(), contentType, content.contentLength());
  }

  public void deleteStoredObjects(List<EventMediaRow> media) {
    for (EventMediaRow mediaRow : media) {
      deleteStoredObject(mediaRow);
    }
  }

  public void deleteStoredObject(EventMediaRow media) {
    if (media == null || trimToNull(media.getBucketName()) == null || trimToNull(media.getStorageKey()) == null) {
      deleteStoredThumbnail(media);
      return;
    }
    objectStorageService.delete(media.getBucketName(), media.getStorageKey());
    deleteStoredThumbnail(media);
  }

  public String mediaContentFilename(EventMediaRow row) {
    String filename = normalizeOriginalFilename(row.getOriginalFilename());
    String extension = storageExtension(row.getMediaType(), firstNonBlank(row.getContentType(), defaultContentType(row.getMediaType())));
    if (filename == null) {
      return "video." + extension;
    }
    String safeFilename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
    return safeFilename.contains(".") ? safeFilename : safeFilename + "." + extension;
  }

  public String normalizeOriginalFilename(String filename) {
    String normalized = trimToNull(filename);
    if (normalized == null) {
      return null;
    }
    int slashIndex = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
    if (slashIndex >= 0) {
      normalized = normalized.substring(slashIndex + 1);
    }
    normalized = normalized.replaceAll("[\\r\\n\\t]", " ").trim();
    return normalized.isBlank() ? null : normalized.substring(0, Math.min(normalized.length(), 255));
  }

  private void validateImageFileSize(int byteSize) {
    if (byteSize <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image is empty.");
    }
    if (byteSize > objectStorageProperties.getMaxFileBytes()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image must be 5 MB or smaller.");
    }
  }

  private void validateVideoFileSize(int byteSize) {
    if (byteSize <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video is empty.");
    }
    if (byteSize > objectStorageProperties.getMaxVideoBytes()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video must be 10 MB or smaller.");
    }
  }

  private void validateImageDimensions(int width, int height) {
    if (width < objectStorageProperties.getMinImageWidth() || height < objectStorageProperties.getMinImageHeight()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "image resolution must be at least "
              + objectStorageProperties.getMinImageWidth()
              + "x"
              + objectStorageProperties.getMinImageHeight()
              + ".");
    }
    if (width > objectStorageProperties.getMaxImageWidth() || height > objectStorageProperties.getMaxImageHeight()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image resolution is too large.");
    }
  }

  private void ensureStorageQuota(int incomingBytes) {
    long usedBytes = eventMapper.sumStoredMediaBytes();
    if (usedBytes + incomingBytes > objectStorageProperties.getMaxTotalBytes()) {
      throw new ResponseStatusException(HttpStatusCode.valueOf(507), "media storage quota is full.");
    }
  }

  private String normalizeImageContentType(String contentType, String filename) {
    String normalized = trimToNull(contentType);
    if (CONTENT_TYPE_JPEG.equalsIgnoreCase(normalized) || "image/jpg".equalsIgnoreCase(normalized)) {
      return CONTENT_TYPE_JPEG;
    }
    if (CONTENT_TYPE_PNG.equalsIgnoreCase(normalized)) {
      return CONTENT_TYPE_PNG;
    }
    String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
      return CONTENT_TYPE_JPEG;
    }
    if (lowerFilename.endsWith(".png")) {
      return CONTENT_TYPE_PNG;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image must be JPEG or PNG.");
  }

  private String normalizeVideoContentType(String contentType, String filename) {
    String normalized = trimToNull(contentType);
    if (CONTENT_TYPE_MP4.equalsIgnoreCase(normalized)) {
      return CONTENT_TYPE_MP4;
    }
    if (CONTENT_TYPE_QUICKTIME.equalsIgnoreCase(normalized)) {
      return CONTENT_TYPE_QUICKTIME;
    }
    if (CONTENT_TYPE_M4V.equalsIgnoreCase(normalized)) {
      return CONTENT_TYPE_M4V;
    }
    String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    if (lowerFilename.endsWith(".mp4")) {
      return CONTENT_TYPE_MP4;
    }
    if (lowerFilename.endsWith(".mov") || lowerFilename.endsWith(".qt")) {
      return CONTENT_TYPE_QUICKTIME;
    }
    if (lowerFilename.endsWith(".m4v")) {
      return CONTENT_TYPE_M4V;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video must be MP4, MOV, or M4V.");
  }

  private String buildStorageKey(String eventId, String mediaType, String contentType) {
    String extension = storageExtension(mediaType, contentType);
    return "events/" + eventId + "/" + mediaType + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
  }

  private void applyImageThumbnail(EventMediaRow media, String eventId, BufferedImage sourceImage) throws IOException {
    BufferedImage thumbnail = resizeThumbnail(sourceImage);
    byte[] bytes = encodeJpeg(thumbnail);
    ensureStorageQuota(bytes.length);
    StoredObject thumbnailObject = objectStorageService.putImage(
        buildStorageKey(eventId, "thumbnail", CONTENT_TYPE_JPEG),
        bytes,
        CONTENT_TYPE_JPEG,
        thumbnail.getWidth(),
        thumbnail.getHeight());

    media.setThumbnailUrl(thumbnailObject.url());
    media.setThumbnailStorageKey(thumbnailObject.storageKey());
    media.setThumbnailBucketName(thumbnailObject.bucketName());
    media.setThumbnailContentType(thumbnailObject.contentType());
    media.setThumbnailByteSize(thumbnailObject.byteSize());
    media.setThumbnailWidth(thumbnailObject.width());
    media.setThumbnailHeight(thumbnailObject.height());
  }

  private BufferedImage resizeThumbnail(BufferedImage sourceImage) {
    double scale = Math.min(
        (double) THUMBNAIL_MAX_WIDTH / sourceImage.getWidth(),
        (double) THUMBNAIL_MAX_HEIGHT / sourceImage.getHeight());
    scale = Math.min(1, scale);
    int width = Math.max(1, (int) Math.round(sourceImage.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(sourceImage.getHeight() * scale));
    BufferedImage thumbnail = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = thumbnail.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.drawImage(sourceImage, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    return thumbnail;
  }

  private byte[] encodeJpeg(BufferedImage image) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "jpg", output)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "thumbnail could not be generated.");
    }
    return output.toByteArray();
  }

  private String storageExtension(String mediaType, String contentType) {
    if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
      if (CONTENT_TYPE_QUICKTIME.equals(contentType)) {
        return "mov";
      }
      if (CONTENT_TYPE_M4V.equals(contentType)) {
        return "m4v";
      }
      return "mp4";
    }
    if ("thumbnail".equals(mediaType)) {
      return "jpg";
    }
    return CONTENT_TYPE_PNG.equals(contentType) ? "png" : "jpg";
  }

  private void deleteStoredThumbnail(EventMediaRow media) {
    if (media == null || trimToNull(media.getThumbnailBucketName()) == null || trimToNull(media.getThumbnailStorageKey()) == null) {
      return;
    }
    objectStorageService.delete(media.getThumbnailBucketName(), media.getThumbnailStorageKey());
  }

  private String resolveBucketName(EventMediaRow media) {
    return firstNonBlank(media.getBucketName(), objectStorageProperties.getBucket());
  }

  private String resolveThumbnailBucketName(EventMediaRow media) {
    return firstNonBlank(media.getThumbnailBucketName(), resolveBucketName(media));
  }

  private String defaultContentType(String mediaType) {
    return MEDIA_TYPE_VIDEO.equals(mediaType) ? CONTENT_TYPE_MP4 : CONTENT_TYPE_JPEG;
  }

  private int countMediaItems(List<EventMediaRow> mediaItems, String mediaType) {
    return (int) mediaItems.stream().filter(item -> mediaType.equals(item.getMediaType())).count();
  }

  private String newMediaId() {
    return MEDIA_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
  }

  private String firstNonBlank(String primary, String fallback) {
    String normalizedPrimary = trimToNull(primary);
    return normalizedPrimary == null ? fallback : normalizedPrimary;
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
