package com.flout.server.domain.supportapplication.service;

import com.flout.server.domain.supportapplication.response.code.SupportAttachmentErrorCode;
import com.flout.server.global.exception.BaseException;
import com.flout.server.global.response.code.GeneralErrorCode;
import java.io.*;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class SupportDocumentValidator {
  public static final int MAX_BYTES = 15 * 1024 * 1024;

  public Document validate(MultipartFile file) {
    if (file == null || file.isEmpty()) throw invalid();
    if (file.getSize() > MAX_BYTES) throw new BaseException(GeneralErrorCode.PAYLOAD_TOO_LARGE);
    String name = file.getOriginalFilename();
    if (name == null
        || name.isBlank()
        || name.length() > 255
        || name.contains("/")
        || name.contains("\\")
        || name.chars().anyMatch(Character::isISOControl)) throw invalid();
    String lower = name.toLowerCase(Locale.ROOT);
    boolean pdf = lower.endsWith(".pdf"),
        jpeg = lower.endsWith(".jpg") || lower.endsWith(".jpeg"),
        png = lower.endsWith(".png");
    if (!pdf && !jpeg && !png) throw invalid();
    byte[] bytes;
    try (var input = file.getInputStream()) {
      bytes = input.readNBytes(MAX_BYTES + 1);
    } catch (IOException e) {
      throw invalid();
    }
    if (bytes.length > MAX_BYTES) throw new BaseException(GeneralErrorCode.PAYLOAD_TOO_LARGE);
    if (bytes.length == 0) throw invalid();
    if (pdf) validatePdf(bytes);
    else validateImage(bytes, jpeg, png);
    return new Document(name, bytes, pdf ? "application/pdf" : jpeg ? "image/jpeg" : "image/png");
  }

  private void validatePdf(byte[] bytes) {
    if (bytes.length < 5
        || bytes[0] != '%'
        || bytes[1] != 'P'
        || bytes[2] != 'D'
        || bytes[3] != 'F'
        || bytes[4] != '-') throw invalid();
    try (var source = new RandomAccessReadBuffer(bytes)) {
      var parser = new PDFParser(source);
      try (var document = parser.parse(false)) {
        if (document.getNumberOfPages() < 1) throw invalid();
        for (var page : document.getPages()) {
          var box = page.getMediaBox();
          if (box == null
              || !Float.isFinite(box.getWidth())
              || !Float.isFinite(box.getHeight())
              || box.getWidth() <= 0
              || box.getHeight() <= 0) throw invalid();
        }
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof BaseException base) throw base;
      throw invalid();
    }
  }

  private void validateImage(byte[] bytes, boolean jpeg, boolean png) {
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
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w <= 0 || h <= 0 || w > 20000 || h > 20000 || (long) w * h > 100_000_000L)
          throw invalid();
        int sample = Math.max(1, (int) Math.ceil(Math.sqrt((double) w * h / 1_000_000L)));
        while (((w + (long) sample - 1) / sample) * ((h + (long) sample - 1) / sample) > 1_000_000L)
          sample *= 2;
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
  }

  private BaseException invalid() {
    return new BaseException(SupportAttachmentErrorCode.INVALID_FILE);
  }

  public record Document(String filename, byte[] bytes, String contentType) {}
}
