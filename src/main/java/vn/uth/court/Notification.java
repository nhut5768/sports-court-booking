package vn.uth.court;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private Account account;

  @ManyToOne(fetch = FetchType.LAZY)
  private Booking booking;

  @Column(nullable = false, length = 300)
  private String message;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  private LocalDateTime readAt;

  public Long getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public void setAccount(Account value) {
    this.account = value;
  }

  public Booking getBooking() {
    return booking;
  }

  public void setBooking(Booking value) {
    this.booking = value;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String value) {
    this.message = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    this.createdAt = value;
  }

  public LocalDateTime getReadAt() {
    return readAt;
  }

  public void setReadAt(LocalDateTime value) {
    this.readAt = value;
  }
}
