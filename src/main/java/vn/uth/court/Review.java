package vn.uth.court;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(unique = true)
  private Booking booking;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private Court court;

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  private Account customer;

  @Column(nullable = false)
  private int rating;

  @Column(length = 1000)
  private String comment;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Column(length = 1000)
  private String ownerReply;

  private LocalDateTime repliedAt;

  public Long getId() {
    return id;
  }

  public Booking getBooking() {
    return booking;
  }

  public void setBooking(Booking value) {
    this.booking = value;
  }

  public Court getCourt() {
    return court;
  }

  public void setCourt(Court value) {
    this.court = value;
  }

  public Account getCustomer() {
    return customer;
  }

  public void setCustomer(Account value) {
    this.customer = value;
  }

  public int getRating() {
    return rating;
  }

  public void setRating(int value) {
    this.rating = value;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String value) {
    this.comment = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    this.createdAt = value;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime value) {
    this.updatedAt = value;
  }

  public String getOwnerReply() {
    return ownerReply;
  }

  public void setOwnerReply(String value) {
    this.ownerReply = value;
  }

  public LocalDateTime getRepliedAt() {
    return repliedAt;
  }

  public void setRepliedAt(LocalDateTime value) {
    this.repliedAt = value;
  }

  /** Tên hiển thị công khai: chỉ tên gọi và chữ cái đầu của họ, ví dụ "Như B." */
  public String reviewerName() {
    String[] parts = customer.getName().trim().split("\\s+");
    if (parts.length == 1) {
      return parts[0];
    }
    return parts[parts.length - 1] + " " + parts[0].charAt(0) + ".";
  }
}
