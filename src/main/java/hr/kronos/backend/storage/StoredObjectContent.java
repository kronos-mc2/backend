package hr.kronos.backend.storage;

import java.util.Arrays;

public record StoredObjectContent(byte[] bytes, String contentType, long contentLength) {
  public StoredObjectContent {
    bytes = bytes == null ? null : bytes.clone();
  }

  @Override
  public byte[] bytes() {
    return bytes == null ? null : bytes.clone();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof StoredObjectContent(byte[] otherBytes, String otherContentType, long otherContentLength))) {
      return false;
    }
    return contentLength == otherContentLength
        && Arrays.equals(bytes, otherBytes)
        && java.util.Objects.equals(contentType, otherContentType);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(bytes);
    result = 31 * result + java.util.Objects.hashCode(contentType);
    result = 31 * result + Long.hashCode(contentLength);
    return result;
  }

  @Override
  public String toString() {
    return "StoredObjectContent[bytesLength=" + (bytes == null ? 0 : bytes.length)
        + ", contentType=" + contentType
        + ", contentLength=" + contentLength
        + "]";
  }
}
