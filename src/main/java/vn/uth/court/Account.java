package vn.uth.court;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, length = 64)
  private String googleSub;

  public String getGoogleSub() { return googleSub; }
  public void setGoogleSub(String value) { googleSub = value; }

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, unique = true, length = 190)
  private String email;

  // Di động Việt Nam 10 số, duy nhất; null với tài khoản cũ có số không hợp lệ
  @Column(unique = true, length = 20)
  private String phone;

  @Column(nullable = false)
  private String password;

  @Column(nullable = false, length = 20)
  private String role;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(length = 20)
  private String bankCode;

  @Column(length = 30)
  private String bankAccountNo;

  @Column(length = 100)
  private String bankAccountName;

  // Ảnh QR nhận tiền chủ sân tự tải lên (MoMo, VietQR tĩnh…)
  @Column(length = 80)
  private String paymentQr;

  @Column(length = 80)
  private String paymentQrType;

  // Đăng nhập bằng mật khẩu tạm do chủ sân cấp thì phải đổi mật khẩu ngay
  @Column(nullable = false)
  private boolean mustChangePassword;

  @Column(nullable=false)
  private boolean locked;
  @Column(nullable=false)
  private long securityVersion;
  public boolean isLocked() { return locked; }
  public void setLocked(boolean value) { locked=value; }
  public long getSecurityVersion() { return securityVersion; }
  public void revokeSessions() { securityVersion++; }
  public boolean isAdmin() { return "ADMIN".equals(role); }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now(AppConstants.ZONE);
    }
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String value) {
    this.email = value;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String value) {
    this.phone = value;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String value) {
    this.password = value;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String value) {
    this.role = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime value) {
    this.createdAt = value;
  }

  public String getBankCode() {
    return bankCode;
  }

  public void setBankCode(String value) {
    this.bankCode = value;
  }

  public String getBankAccountNo() {
    return bankAccountNo;
  }

  public void setBankAccountNo(String value) {
    this.bankAccountNo = value;
  }

  public String getBankAccountName() {
    return bankAccountName;
  }

  public void setBankAccountName(String value) {
    this.bankAccountName = value;
  }

  public String getPaymentQr() {
    return paymentQr;
  }

  public void setPaymentQr(String value) {
    this.paymentQr = value;
  }

  public String getPaymentQrType() {
    return paymentQrType;
  }

  public void setPaymentQrType(String value) {
    this.paymentQrType = value;
  }

  public boolean getMustChangePassword() {
    return mustChangePassword;
  }

  public void setMustChangePassword(boolean value) {
    this.mustChangePassword = value;
  }

  public boolean hasBankAccount() {
    return bankCode != null && bankAccountNo != null && bankAccountName != null;
  }

  public boolean hasPaymentQr() {
    return paymentQr != null;
  }

  /** Có ảnh QR tải lên hoặc tài khoản ngân hàng để tạo VietQR. */
  public boolean canReceiveDeposit() {
    return hasPaymentQr() || hasBankAccount();
  }

  public boolean isOwner() {
    return "OWNER".equals(role);
  }
}
