package vn.uth.court;
import jakarta.persistence.*;

@Entity
@Table(name = "stored_uploads")
public class StoredUpload {
  @Id @Column(name = "object_key", length = 160, nullable = false)
  private String key;
  @Lob @Column(name = "content", nullable = false, columnDefinition = "LONGBLOB")
  private byte[] content;
  protected StoredUpload() {}
  public StoredUpload(String key, byte[] content) { this.key = key; this.content = content; }
  public byte[] getContent() { return content; }
}
