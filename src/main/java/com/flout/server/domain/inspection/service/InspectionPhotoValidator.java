package com.flout.server.domain.inspection.service;

import com.flout.server.domain.inspection.response.code.InspectionErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class InspectionPhotoValidator {
  private static final int MAX_BYTES = 10 * 1024 * 1024;

  public ValidatedPhoto validate(MultipartFile file) {
    if (file == null || file.isEmpty()) throw new BaseException(InspectionErrorCode.EMPTY_PHOTOS);
    if (file.getSize() > MAX_BYTES) throw new BaseException(GeneralErrorCode.PAYLOAD_TOO_LARGE);
    String name = file.getOriginalFilename();
    if (name == null
        || name.isBlank()
        || name.length() > 255
        || name.contains("/")
        || name.contains("\\")
        || name.chars().anyMatch(Character::isISOControl)) throw invalid();
    String lower = name.toLowerCase(Locale.ROOT);
    boolean jpeg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    boolean png = lower.endsWith(".png");
    if (!jpeg && !png) throw invalid();
    byte[] bytes = read(file);
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (input == null) throw invalid();
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw invalid();
      var reader = readers.next();
      try {
        reader.setInput(input);
        String format = reader.getFormatName();
        if (!(jpeg && ("jpeg".equalsIgnoreCase(format) || "jpg".equalsIgnoreCase(format)))
            && !(png && "png".equalsIgnoreCase(format))) throw invalid();
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width <= 0
            || height <= 0
            || width > 20000
            || height > 20000
            || (long) width * height > 100_000_000L) throw invalid();
        int sample = Math.max(1, (int) Math.ceil(Math.sqrt((double) width * height / 1_000_000L)));
        while (((width + (long) sample - 1) / sample) * ((height + (long) sample - 1) / sample)
            > 1_000_000L) sample *= 2;
        var parameters = reader.getDefaultReadParam();
        parameters.setSourceSubsampling(sample, sample, 0, 0);
        if (reader.read(0, parameters) == null) throw invalid();
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof BaseException base) throw base;
      throw invalid();
    }
    return new ValidatedPhoto(
        file, name, bytes.length, jpeg ? "image/jpeg" : "image/png", hash(bytes));
  }

  public byte[] bytesForUpload(ValidatedPhoto photo) {
    byte[] bytes = read(photo.file());
    if (bytes.length != photo.size() || !hash(bytes).equals(photo.hash())) throw invalid();
    return bytes;
  }

  private byte[] read(MultipartFile file) {
    try (var input = file.getInputStream()) {
      byte[] bytes = input.readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) throw new BaseException(GeneralErrorCode.PAYLOAD_TOO_LARGE);
      if (bytes.length == 0) throw new BaseException(InspectionErrorCode.EMPTY_PHOTOS);
      return bytes;
    } catch (IOException e) {
      throw invalid();
    }
  }

  private String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private BaseException invalid() {
    return new BaseException(InspectionErrorCode.INVALID_PHOTOS);
  }

  public record ValidatedPhoto(
      MultipartFile file, String filename, long size, String contentType, String hash) {}
}
