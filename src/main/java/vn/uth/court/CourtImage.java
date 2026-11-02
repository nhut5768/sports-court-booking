package vn.uth.court;

import jakarta.persistence.*;

@Entity
@Table(name = "court_images")
public class CourtImage {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private Court court;

  @Column(nullable = false, unique = true, length = 80)
  private String storedName;

  @Column(nullable = false, length = 80)
  private String contentType;

  @Column(nullable = false)
  private int sortOrder;

  public Long getId() {
    return id;
  }

  public Court getCourt() {
    return court;
  }

  public void setCourt(Court value) {
    this.court = value;
  }

  public String getStoredName() {
    return storedName;
  }

  public void setStoredName(String value) {
    this.storedName = value;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String value) {
    this.contentType = value;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int value) {
    this.sortOrder = value;
  }
}
