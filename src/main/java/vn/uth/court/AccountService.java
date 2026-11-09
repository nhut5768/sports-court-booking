package vn.uth.court;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AccountService {
  // Bỏ các ký tự dễ nhầm khi đọc qua điện thoại (0/O, 1/l/I)
  private static final String TEMP_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

  private final AccountRepository accounts;
  private final BookingRepository bookings;
  private final PasswordEncoder encoder;
  private final ImageStorageService storage;
  private final SecureRandom random = new SecureRandom();

  public AccountService(
      AccountRepository accounts,
      BookingRepository bookings,
      PasswordEncoder encoder,
      ImageStorageService storage) {
    this.accounts = accounts;
    this.bookings = bookings;
    this.encoder = encoder;
    this.storage = storage;
  }

  @Transactional
  public void changePassword(String email, String current, String password, String confirm) {
    Account a = current(email);
    if (current == null || !encoder.matches(current, a.getPassword())) {
      throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
    }
    checkNewPassword(password, confirm);
    if (encoder.matches(password, a.getPassword())) {
      throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
    }
    a.setPassword(encoder.encode(password));
    a.setMustChangePassword(false);
    accounts.saveAndFlush(a);
  }

  /**
   * Khách quên mật khẩu gọi cho chủ sân; chủ sân cấp mật khẩu tạm (chỉ cho khách đã từng đặt sân
   * của mình) và đọc cho khách. Khách đăng nhập bằng mật khẩu tạm thì bắt buộc đổi ngay.
   */
  @Transactional
  public String issueTemporaryPassword(String ownerEmail, Long customerId) {
    Account owner = current(ownerEmail);
    Account customer =
        accounts
            .findById(customerId)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng."));
    if (!owner.isOwner()
        || !"CUSTOMER".equals(customer.getRole())
        || !bookings.existsByCustomerIdAndCourtOwnerId(customer.getId(), owner.getId())) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Chỉ cấp mật khẩu tạm cho khách đã đặt sân của bạn.");
    }
    if (customer.getGoogleSub() != null) {
      throw new IllegalArgumentException("Tài khoản này đăng nhập bằng Google; không cấp mật khẩu tạm.");
    }
    StringBuilder temp = new StringBuilder();
    for (int i = 0; i < 10; i++) {
      temp.append(TEMP_CHARS.charAt(random.nextInt(TEMP_CHARS.length())));
    }
    customer.setPassword(encoder.encode(temp.toString()));
    customer.setMustChangePassword(true);
    accounts.saveAndFlush(customer);
    return temp.toString();
  }

  private static void checkNewPassword(String password, String confirm) {
    if (password == null || password.length() < 8 || password.length() > 64) {
      throw new IllegalArgumentException("Mật khẩu mới cần 8–64 ký tự.");
    }
    if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalArgumentException(
          "Mật khẩu quá dài khi mã hóa UTF-8; vui lòng dùng mật khẩu ngắn hơn.");
    }
    if (!password.equals(confirm)) {
      throw new IllegalArgumentException("Mật khẩu nhập lại không khớp.");
    }
  }

  /** Tìm tài khoản theo email hoặc số điện thoại (dùng cho đăng nhập). */
  public Optional<Account> findByLogin(String login) {
    if (login == null || login.isBlank()) {
      return Optional.empty();
    }
    String id = login.trim();
    if (id.contains("@")) {
      return accounts.findByEmail(id.toLowerCase(Locale.ROOT));
    }
    String phone = Phones.normalize(id);
    return Phones.valid(phone) ? accounts.findByPhone(phone) : Optional.empty();
  }

  @Transactional
  public void updatePhone(String email, String rawPhone) {
    Account a = current(email);
    String phone = Phones.require(rawPhone);
    if (phone.equals(a.getPhone())) {
      return;
    }
    if (accounts.existsByPhone(phone)) {
      throw new IllegalArgumentException("Số điện thoại đã được dùng cho tài khoản khác.");
    }
    a.setPhone(phone);
    accounts.saveAndFlush(a);
  }

  @Transactional
  public void updatePaymentQr(String email, MultipartFile file) {
    Account a = current(email);
    if (!a.isOwner()) {
      throw new IllegalArgumentException("Chỉ chủ sân cần mã QR nhận cọc.");
    }
    ImageStorageService.StoredImage stored = storage.save(file);
    storage.writePaymentQr(a.getId(), stored.storedName(), file);
    String old = a.getPaymentQr();
    a.setPaymentQr(stored.storedName());
    a.setPaymentQrType(stored.contentType());
    accounts.saveAndFlush(a);
    if (old != null) {
      storage.deletePaymentQr(a.getId(), old);
    }
  }

  @Transactional
  public void removePaymentQr(String email) {
    Account a = current(email);
    if (a.getPaymentQr() == null) {
      return;
    }
    String old = a.getPaymentQr();
    a.setPaymentQr(null);
    a.setPaymentQrType(null);
    accounts.saveAndFlush(a);
    storage.deletePaymentQr(a.getId(), old);
  }

  @Transactional
  public Account register(RegistrationForm f) { return registerNew(f,"CUSTOMER"); }

  @Transactional
  public Account registerOwner(RegistrationForm f) { return registerNew(f,"OWNER"); }

  private Account registerNew(RegistrationForm f,String fixedRole) {
    if (f.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalArgumentException(
          "Mật khẩu quá dài khi mã hóa UTF-8; vui lòng dùng mật khẩu ngắn hơn.");
    }
    String email = f.getEmail().trim().toLowerCase(Locale.ROOT);
    if (accounts.findByEmailIgnoreCase(email).isPresent()) {
      throw new IllegalArgumentException("Email đã được đăng ký.");
    }
    String phone = Phones.require(f.getPhone());
    if (accounts.existsByPhone(phone)) {
      throw new IllegalArgumentException("Số điện thoại đã được đăng ký.");
    }
    Account a = new Account();
    a.setName(f.getName().trim());
    a.setEmail(email);
    a.setPhone(phone);
    a.setPassword(encoder.encode(f.getPassword()));
    a.setRole(fixedRole);
    a.setMustChangePassword(false);
    return accounts.saveAndFlush(a);
  }

  @Transactional
  public void updateBank(String email, String bankCode, String accountNo, String accountName) {
    Account a = current(email);
    if (!a.isOwner()) {
      throw new IllegalArgumentException("Chỉ chủ sân cần tài khoản nhận cọc.");
    }
    String code = bankCode == null ? "" : bankCode.trim();
    String no = accountNo == null ? "" : accountNo.replaceAll("\\s", "");
    // VietQR yêu cầu tên chủ tài khoản viết hoa, không dấu
    String name =
        Normalizer.normalize(accountName == null ? "" : accountName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .trim()
            .replaceAll("\\s+", " ")
            .toUpperCase(Locale.ROOT);
    if (!AppConstants.BANKS.containsKey(code)) {
      throw new IllegalArgumentException("Chọn ngân hàng hợp lệ.");
    }
    if (!no.matches("\\d{6,20}")) {
      throw new IllegalArgumentException("Số tài khoản gồm 6–20 chữ số.");
    }
    if (!name.matches("[A-Z ]{2,100}")) {
      throw new IllegalArgumentException("Nhập tên chủ tài khoản đúng như trên ngân hàng.");
    }
    a.setBankCode(code);
    a.setBankAccountNo(no);
    a.setBankAccountName(name);
    accounts.saveAndFlush(a);
  }

  public Account current(String email) {
    return accounts
        .findByEmail(email)
        .filter(a -> !a.isLocked())
        .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại."));
  }
}
