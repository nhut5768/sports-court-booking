package vn.uth.court;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageStorageService {

  private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

  private static final Set<String> NAMES =
      Set.of(".jpg", ".jpeg", ".png", ".webp");

  private final Path root;
  private final boolean database;
  private final StoredUploadRepository uploads;

  public ImageStorageService(
      @Value("${app.upload-dir}") String dir,
      @Value("${app.storage.mode:filesystem}") String mode,
      StoredUploadRepository uploads) throws IOException {
    if (!Set.of("filesystem", "database").contains(mode)) throw new IllegalArgumentException("Invalid storage mode");
    this.database = "database".equals(mode);
    this.uploads = uploads;

    this.root = Path.of(dir).toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  public StoredImage save(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Chưa chọn tệp ảnh.");
    }

    if (file.getSize() > MAX_IMAGE_BYTES) {
      throw new IllegalArgumentException("Ảnh vượt quá 10 MB.");
    }

    Detected type = detect(file);
    String storedName = UUID.randomUUID() + "." + type.ext();

    return new StoredImage(storedName, type.mediaType());
  }

  public void write(
      Long courtId,
      String storedName,
      MultipartFile file) {
    write(courtDir(courtId), storedName, file);
  }

  public void writePaymentQr(Long ownerId, String storedName, MultipartFile file) {
    write(paymentQrDir(ownerId), storedName, file);
  }

  public Path resolvePaymentQr(Long ownerId, String storedName) {
    return resolveIn(paymentQrDir(ownerId), storedName);
  }

  public void deletePaymentQr(Long ownerId, String storedName) {
    deleteIn(paymentQrDir(ownerId), storedName);
  }

  private void write(Path dir, String storedName, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Chưa chọn tệp ảnh.");
    }

    if (file.getSize() > MAX_IMAGE_BYTES) {
      throw new IllegalArgumentException("Ảnh vượt quá 10 MB.");
    }

    validateStoredName(storedName);
    if (database) {
      detect(file);
      try {
        uploads.save(new StoredUpload(objectKey(dir, storedName), file.getBytes()));
        return;
      } catch (IOException e) { throw new IllegalStateException("Không lưu được ảnh.", e); }
    }

    try {
      Files.createDirectories(dir);

      Path destination = dir.resolve(storedName).normalize();

      if (!destination.startsWith(dir)) {
        throw new IllegalArgumentException("Tên tệp không hợp lệ.");
      }

      try (InputStream input = file.getInputStream()) {
        Files.copy(input, destination);
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Không lưu được ảnh. Vui lòng thử lại.",
          exception);
    }
  }

  public Path resolve(Long courtId, String storedName) {
    return resolveIn(courtDir(courtId), storedName);
  }

  private Path resolveIn(Path dir, String storedName) {
    validateStoredName(storedName);

    Path destination = dir.resolve(storedName).normalize();

    if (database && !Files.isRegularFile(destination)) {
      var original = uploads.findById(objectKey(dir, storedName))
          .orElseThrow(() -> new NotFoundException("Không tìm thấy ảnh."));
      try {
        Files.createDirectories(dir);
        Path temporary = Files.createTempFile(dir, "restore-", ".tmp");
        try {
          Files.write(temporary, original.getContent());
          Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
      } catch (IOException e) { throw new IllegalStateException("Không đọc được ảnh.", e); }
    }
    if (!destination.startsWith(dir)
        || !Files.isRegularFile(destination)) {
      throw new NotFoundException("Không tìm thấy ảnh.");
    }

    return destination;
  }

  public void delete(Long courtId, String storedName) {
    deleteIn(courtDir(courtId), storedName);
  }

  private String objectKey(Path dir, String storedName) {
    return root.relativize(dir.resolve(storedName)).toString().replace('\\', '/');
  }

  private void deleteIn(Path dir, String storedName) {
    validateStoredName(storedName);
    if (database) uploads.deleteById(objectKey(dir, storedName));

    try {
      Path destination = dir.resolve(storedName).normalize();

      if (!destination.startsWith(dir)) {
        throw new IllegalArgumentException("Tên tệp không hợp lệ.");
      }

      Files.deleteIfExists(destination);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Không xóa được ảnh.",
          exception);
    }
  }

  private Path courtDir(Long courtId) {
    if (courtId == null || courtId <= 0) {
      throw new IllegalArgumentException("Mã sân không hợp lệ.");
    }

    return root.resolve("courts")
        .resolve(String.valueOf(courtId))
        .normalize();
  }

  private Path paymentQrDir(Long ownerId) {
    if (ownerId == null || ownerId <= 0) {
      throw new IllegalArgumentException("Mã tài khoản không hợp lệ.");
    }

    return root.resolve("payment-qr")
        .resolve(String.valueOf(ownerId))
        .normalize();
  }

  private void validateStoredName(String storedName) {
    if (storedName == null
        || !storedName.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                + "[0-9a-fA-F]{12}\\.(jpg|png|webp)")) {
      throw new NotFoundException("Không tìm thấy ảnh.");
    }
  }

  private Detected detect(MultipartFile file) {
    byte[] header;

    try (InputStream input = file.getInputStream()) {
      header = input.readNBytes(12);
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "Không đọc được tệp ảnh.",
          exception);
    }

    // JPEG
    if (header.length >= 3
        && header[0] == (byte) 0xFF
        && header[1] == (byte) 0xD8
        && header[2] == (byte) 0xFF) {
      return new Detected("jpg", MediaType.IMAGE_JPEG_VALUE);
    }

    // PNG: kiểm tra đủ 8 byte chữ ký.
    if (header.length >= 8
        && header[0] == (byte) 0x89
        && header[1] == 0x50
        && header[2] == 0x4E
        && header[3] == 0x47
        && header[4] == 0x0D
        && header[5] == 0x0A
        && header[6] == 0x1A
        && header[7] == 0x0A) {
      return new Detected("png", MediaType.IMAGE_PNG_VALUE);
    }

    // WEBP
    if (header.length >= 12
        && header[0] == 'R'
        && header[1] == 'I'
        && header[2] == 'F'
        && header[3] == 'F'
        && header[8] == 'W'
        && header[9] == 'E'
        && header[10] == 'B'
        && header[11] == 'P') {
      return new Detected("webp", "image/webp");
    }

    String originalName = file.getOriginalFilename();

    String name =
        originalName == null
            ? ""
            : originalName.toLowerCase(Locale.ROOT);

    if (NAMES.stream().anyMatch(name::endsWith)) {
      throw new IllegalArgumentException(
          "Chữ ký tệp không khớp định dạng JPEG, PNG hoặc WEBP.");
    }

    throw new IllegalArgumentException(
        "Chỉ chấp nhận ảnh JPEG, PNG hoặc WEBP.");
  }

  public record StoredImage(
      String storedName,
      String contentType) {}

  private record Detected(
      String ext,
      String mediaType) {}
}