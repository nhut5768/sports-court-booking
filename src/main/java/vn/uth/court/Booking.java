package vn.uth.court;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  private Court court;

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  private Account customer;

  @Column(nullable = false)
  private LocalDate playDate;

  @Column(nullable = false)
  private int startHour;

  @Column(nullable = false, precision = 12, scale = 0)
  private BigDecimal amount;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(unique = true, length = 80)
  private String activeSlotKey;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  // null với đơn cũ được tạo trước khi áp dụng đặt cọc
  @Column(precision = 12, scale = 0)
  private BigDecimal depositAmount;

  private LocalDateTime committedAt;

  private LocalDateTime depositSentAt;

  private LocalDateTime depositConfirmedAt;

  @Column(nullable = false)
  private boolean depositForfeited;

  // Đơn chờ cọc giữ khung giờ đến thời điểm này, quá hạn thì tự hủy
  private LocalDateTime holdUntil;

  public Long getId() {
    return id;
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

  public LocalDate getPlayDate() {
    return playDate;
  }

  public void setPlayDate(LocalDate value) {
    this.playDate = value;
  }

  public int getStartHour() {
    return startHour;
  }

  public void setStartHour(int value) {
    this.startHour = value;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal value) {
    this.amount = value;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String value) {
    this.status = value;
  }

  public String getActiveSlotKey() {
    return activeSlotKey;
  }

  public void setActiveSlotKey(String value) {
    this.activeSlotKey = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    this.createdAt = value;
  }

  public BigDecimal getDepositAmount() {
    return depositAmount;
  }

  public void setDepositAmount(BigDecimal value) {
    this.depositAmount = value;
  }

  public LocalDateTime getCommittedAt() {
    return committedAt;
  }

  public void setCommittedAt(LocalDateTime value) {
    this.committedAt = value;
  }

  public LocalDateTime getDepositSentAt() {
    return depositSentAt;
  }

  public void setDepositSentAt(LocalDateTime value) {
    this.depositSentAt = value;
  }

  public LocalDateTime getDepositConfirmedAt() {
    return depositConfirmedAt;
  }

  public void setDepositConfirmedAt(LocalDateTime value) {
    this.depositConfirmedAt = value;
  }

  public LocalDateTime getHoldUntil() {
    return holdUntil;
  }

  public void setHoldUntil(LocalDateTime value) {
    this.holdUntil = value;
  }

  public boolean getDepositForfeited() {
    return depositForfeited;
  }

  public void setDepositForfeited(boolean value) {
    this.depositForfeited = value;
  }

  /** Đơn còn giữ khung giờ: chờ cọc, chờ xác nhận cọc hoặc đã xác nhận. */
  public boolean isActive() {
    return AppConstants.ACTIVE_STATUSES.contains(status);
  }

  /** Khách đã báo chuyển cọc; hủy từ đây sẽ mất cọc. */
  public boolean depositPaid() {
    return depositSentAt != null;
  }

  /** Chủ sân cần kiểm tra tiền cọc: khách đã báo chuyển nhưng chưa được xác nhận. */
  public boolean needsDepositCheck() {
    return depositSentAt != null
        && depositConfirmedAt == null
        && ("DEPOSIT_SENT".equals(status) || depositForfeited);
  }

  public BigDecimal remainingAmount() {
    return depositAmount == null ? amount : amount.subtract(depositAmount);
  }

  public String transferNote() {
    return "SANNHA " + id;
  }

  public String statusLabel() {
    return switch (status) {
      case "PENDING_DEPOSIT" -> "Chờ đặt cọc";
      case "DEPOSIT_SENT" -> "Chờ xác nhận cọc";
      case "CONFIRMED" -> depositAmount == null ? "Đã xác nhận" : "Đã cọc";
      case "CANCELLED" -> "Đã hủy";
      case "COMPLETED" -> "Hoàn tất / đã thu";
      default -> status;
    };
  }
}
