package vn.uth.court;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"app.demo.seed=false"})
@ActiveProfiles("demo")
class DatabaseImageStorageTest {
  @Autowired StoredUploadRepository originals;
  @Autowired PlatformTransactionManager transactions;
  @TempDir Path disk;
  MockMultipartFile image() {
    return new MockMultipartFile("file", "test.png", "image/png",
        new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,0,0,0,0});
  }
  @BeforeEach void clear() { originals.deleteAll(); }
  @Test void originalsSurviveLosingTheEntireRuntimeCache() throws Exception {
    var first = new ImageStorageService(disk.resolve("first").toString(),"database",originals);
    var file = image(); var name = first.save(file).storedName();
    new TransactionTemplate(transactions).executeWithoutResult(s -> first.write(7L,name,file));
    assertThat(Files.readAllBytes(first.resolve(7L,name))).isEqualTo(file.getBytes());
    var restarted = new ImageStorageService(disk.resolve("new-container").toString(),"database",originals);
    assertThat(Files.readAllBytes(restarted.resolve(7L,name))).isEqualTo(file.getBytes());
    assertThatThrownBy(() -> restarted.resolve(8L,name)).isInstanceOf(NotFoundException.class);
  }
  @Test void courtAndPaymentQrAreIsolatedAndDeleteRemovesTheOriginal() throws Exception {
    var store = new ImageStorageService(disk.toString(),"database",originals);
    var file = image(); var name = store.save(file).storedName();
    store.write(1L,name,file); store.writePaymentQr(1L,name,file);
    store.resolve(1L,name); store.resolvePaymentQr(1L,name);
    store.delete(1L,name);
    assertThatThrownBy(() -> store.resolve(1L,name)).isInstanceOf(NotFoundException.class);
    assertThat(Files.readAllBytes(store.resolvePaymentQr(1L,name))).isEqualTo(file.getBytes());
    assertThat(originals.count()).isEqualTo(1);
  }
  @Test void aRolledBackUploadDoesNotLeaveAnOriginalInTheDatabase() throws Exception {
    var store = new ImageStorageService(disk.toString(),"database",originals);
    var name = store.save(image()).storedName();
    assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> {
      store.write(1L,name,image());
      throw new IllegalStateException("rollback");
    })).isInstanceOf(IllegalStateException.class);
    assertThat(originals.count()).isZero();
    assertThatThrownBy(() -> store.resolve(1L,name)).isInstanceOf(NotFoundException.class);
  }
  @Test void traversalAndFakeImageAreRejectedWithoutSavingAnything() throws Exception {
    var store = new ImageStorageService(disk.toString(),"database",originals);
    assertThatThrownBy(() -> store.write(1L,"../file.png",image())).isInstanceOf(NotFoundException.class);
    var fake = new MockMultipartFile("file","bad.png","image/png","not an image".getBytes());
    assertThatThrownBy(() -> store.write(1L,UUID.randomUUID()+".png",fake)).isInstanceOf(IllegalArgumentException.class);
    assertThat(originals.count()).isZero();
  }
}
