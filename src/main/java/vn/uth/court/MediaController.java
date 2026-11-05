package vn.uth.court;

import java.io.IOException;
import java.nio.file.Files;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class MediaController {
  private final CourtImageRepository images;
  private final AccountRepository accounts;
  private final ImageStorageService storage;
  private final CourtPhotoPreview previews;

  public MediaController(
      CourtImageRepository images, AccountRepository accounts, ImageStorageService storage, CourtPhotoPreview previews) {
    this.images = images;
    this.accounts = accounts;
    this.storage = storage;
    this.previews = previews;
  }

  // Ngoài /media/** nên cần đăng nhập mới xem được QR nhận cọc
  @GetMapping("/payment-qr/{ownerId}/{filename}")
  public ResponseEntity<Resource> paymentQr(
      @PathVariable Long ownerId, @PathVariable String filename) throws IOException {
    Account owner =
        accounts
            .findById(ownerId)
            .filter(a -> filename.equals(a.getPaymentQr()))
            .orElseThrow(() -> new NotFoundException("Không tìm thấy ảnh."));
    var path = storage.resolvePaymentQr(ownerId, filename);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(owner.getPaymentQrType()))
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
        .contentLength(Files.size(path))
        .body(new FileSystemResource(path));
  }

  @GetMapping("/media/courts/{courtId}/{filename}")
  public ResponseEntity<Resource> image(@PathVariable Long courtId, @PathVariable String filename,
      @org.springframework.web.bind.annotation.RequestParam(required=false) Integer width)
      throws IOException {
    CourtImage img =
        images.findByCourtIdOrderBySortOrderAsc(courtId).stream()
            .filter(i -> i.getStoredName().equals(filename))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Không tìm thấy ảnh."));
    var path = storage.resolve(courtId, filename);
    MediaType type = MediaType.parseMediaType(img.getContentType());
    if (!type.getType().equals("image")) {
      throw new NotFoundException("Không tìm thấy ảnh.");
    }
    if(width!=null){
      var preview=previews.preview(path,width);
      if(preview.isPresent())return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
          .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CACHE_CONTROL,"public, max-age=86400")
          .contentLength(preview.get().length).body(new org.springframework.core.io.ByteArrayResource(preview.get()));
    }
    return ResponseEntity.ok()
        .contentType(type)
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(filename).build().toString())
        .contentLength(Files.size(path))
        .body(new FileSystemResource(path));
  }
}
