package vn.uth.court;
import static org.assertj.core.api.Assertions.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CourtPhotoPreviewTest {
  @TempDir Path dir;
  @Test void derivesSmallerImageWithoutChangingUploadedOriginal() throws Exception {
    var path=dir.resolve("court.png");var original=new BufferedImage(1600,1000,BufferedImage.TYPE_INT_RGB);ImageIO.write(original,"png",path.toFile());byte[] before=Files.readAllBytes(path);
    var previews=new CourtPhotoPreview();byte[] bytes=previews.preview(path,480).orElseThrow();
    var result=ImageIO.read(new ByteArrayInputStream(bytes));assertThat(result.getWidth()).isEqualTo(480);assertThat(result.getHeight()).isEqualTo(300);assertThat(Files.readAllBytes(path)).isEqualTo(before);
    assertThat(previews.preview(path,480).orElseThrow()).isSameAs(bytes);
  }
  @Test void limitsWidthsAndFallsBackWhenFormatCannotBeDecoded() throws Exception {
    var path=dir.resolve("unknown.webp");Files.writeString(path,"not a decodable image");var previews=new CourtPhotoPreview();
    assertThat(previews.preview(path,800)).isEmpty();assertThatThrownBy(()->previews.preview(path,12000)).isInstanceOf(IllegalArgumentException.class);
  }
}
