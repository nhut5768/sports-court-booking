package vn.uth.court;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class BookingService {
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
  public record Slot(int hour, String label, String state, BigDecimal price) {}

  private final CourtRepository courts;
  private final BookingRepository bookings;
  private final BlockedSlotRepository blocks;
  private final PriceRuleRepository prices;
  private final CourtImageRepository images;
  private final ImageStorageService storage;
  private final NotificationRepository notifications;
  private final Clock clock;

  public BookingService(
      CourtRepository courts,
      BookingRepository bookings,
      BlockedSlotRepository blocks,
      PriceRuleRepository prices,
      CourtImageRepository images,
      ImageStorageService storage,
      NotificationRepository notifications,
      Clock clock) {
    this.courts = courts;
    this.bookings = bookings;
    this.blocks = blocks;
    this.prices = prices;
    this.images = images;
    this.storage = storage;
    this.notifications = notifications;
    this.clock = clock;
  }

  public LocalDate today() {
    return LocalDate.now(clock);
  }

  public LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public static String key(Long courtId, LocalDate date, int hour) {
    return courtId + ":" + date + ":" + hour;
  }

  public Court requireCourt(Long id) {
    return courts.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy sân."));
  }

  @Transactional(readOnly = true)
  public Court loadDetails(Long id) {
    Court c = requireCourt(id);
    c.getPriceRules().size();
    c.getImages().size();
    return c;
  }

  private Court lock(Long id) {
    return courts.lockById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy sân."));
  }

  public void requireOwner(Court court, Account user) {
    if (user.isLocked() || !user.isOwner() || !court.getOwner().getId().equals(user.getId())) {
      throw new AccessDeniedException("Bạn không quản lý sân này.");
    }
  }

  public Booking requireOwnedBooking(Long id, Account actor) {
    Booking b =
        bookings.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy đơn."));
    boolean owner = b.getCourt().getOwner().getId().equals(actor.getId());
    boolean customer = b.getCustomer().getId().equals(actor.getId());
    if (!owner && !customer) {
      throw new AccessDeniedException("Bạn không được xem đơn này.");
    }
    if (owner && !actor.isOwner()) {
      throw new AccessDeniedException("Bạn không được xem đơn này.");
    }
    return b;
  }

  public void checkDate(LocalDate date) {
    if (date == null
        || date.isBefore(today())
        || date.isAfter(today().plusDays(AppConstants.MAX_BOOKING_DAYS))) {
      throw new IllegalArgumentException("Chỉ xem và đặt lịch từ hôm nay đến 30 ngày tới.");
    }
  }

  public BigDecimal priceFor(Court court, int hour) {
    for (PriceRule rule : prices.findByCourtIdOrderByStartHourAsc(court.getId())) {
      if (hour >= rule.getStartHour() && hour < rule.getEndHour()) {
        return rule.getPrice();
      }
    }
    return court.getPrice();
  }

  private void futureSlot(Court c, LocalDate date, int hour) {
    checkDate(date);
    if (hour < c.getOpenHour() || hour >= c.getCloseHour()) {
      throw new IllegalArgumentException("Khung giờ nằm ngoài giờ mở cửa.");
    }
    if (!date.atTime(hour, 0).isAfter(now())) {
      throw new IllegalArgumentException("Khung giờ đã bắt đầu.");
    }
  }

  @Transactional(readOnly = true)
  public List<Slot> slots(Court c, LocalDate date) {
    checkDate(date);
    Set<Integer> taken = new HashSet<>();
    Set<Integer> blocked = new HashSet<>();
    bookings.findByCourtIdAndPlayDate(c.getId(), date).stream()
        .filter(Booking::isActive)
        .forEach(b -> taken.add(b.getStartHour()));
    blocks.findByCourtIdAndPlayDate(c.getId(), date).forEach(b -> blocked.add(b.getStartHour()));
    List<Slot> out = new ArrayList<>();
    for (int h = c.getOpenHour(); h < c.getCloseHour(); h++) {
      String state =
          !date.atTime(h, 0).isAfter(now())
              ? "PAST"
              : taken.contains(h)
                  ? "BOOKED"
                  : blocked.contains(h) ? "BLOCKED" : !c.getActive() ? "CLOSED" : "FREE";
      out.add(
          new Slot(h, String.format("%02d:00 – %02d:00", h, h + 1), state, priceFor(c, h)));
    }
    return out;
  }

  public void assertBookable(Court c, LocalDate date, int hour) {
    futureSlot(c, date, hour);
    if (!c.getActive()) {
      throw new IllegalArgumentException("Sân đang ngừng nhận đặt.");
    }
    String slotKey = key(c.getId(), date, hour);
    if (blocks.existsBySlotKey(slotKey) || bookings.existsByActiveSlotKey(slotKey)) {
      throw new IllegalArgumentException("Khung giờ vừa được đặt hoặc đã bị khóa. Vui lòng chọn giờ khác.");
    }
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Booking book(Long courtId, LocalDate date, int hour, Account customer) {
    return book(courtId, date, hour, customer, null);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Booking book(Long courtId, LocalDate date, int hour, Account customer, BigDecimal expectedPrice) {
    if (!"CUSTOMER".equals(customer.getRole())) {
      throw new AccessDeniedException("Chỉ khách hàng được đặt sân trên tài khoản này.");
    }
    if (customer.getPhone() == null || customer.getPhone().isBlank()) {
      throw new IllegalArgumentException(
          "Vui lòng cập nhật số điện thoại trong trang Tài khoản trước khi đặt sân.");
    }
    Court c = lock(courtId);
    // Đơn chờ cọc đã quá hạn giữ chỗ nhưng tác vụ định kỳ chưa kịp hủy thì hủy ngay tại đây
    bookings
        .findByActiveSlotKey(key(courtId, date, hour))
        .filter(this::holdExpired)
        .ifPresent(this::expireHold);
    assertBookable(c, date, hour);
    BigDecimal currentPrice = priceFor(c, hour);
    if (expectedPrice != null && expectedPrice.compareTo(currentPrice) != 0) {
      throw new IllegalArgumentException("Giá khung giờ đã thay đổi. Vui lòng chọn lại giờ và kiểm tra giá mới trước khi đặt.");
    }
    Booking b = new Booking();
    b.setCourt(c);
    b.setCustomer(customer);
    b.setPlayDate(date);
    b.setStartHour(hour);
    b.setAmount(currentPrice);
    b.setDepositAmount(depositFor(b.getAmount()));
    b.setStatus("PENDING_DEPOSIT");
    b.setActiveSlotKey(key(courtId, date, hour));
    b.setCreatedAt(now());
    b.setHoldUntil(now().plusMinutes(AppConstants.DEPOSIT_HOLD_MINUTES));
    return bookings.saveAndFlush(b);
  }

  private boolean holdExpired(Booking b) {
    return "PENDING_DEPOSIT".equals(b.getStatus())
        && b.getHoldUntil() != null
        && !now().isBefore(b.getHoldUntil());
  }

  private void requireHoldActive(Booking b) {
    if (holdExpired(b)) {
      throw new IllegalArgumentException(
          "Đơn đã quá "
              + AppConstants.DEPOSIT_HOLD_MINUTES
              + " phút giữ chỗ mà chưa chuyển cọc nên bị hủy. Vui lòng đặt lại.");
    }
  }

  private void expireHold(Booking b) {
    b.setStatus("CANCELLED");
    b.setActiveSlotKey(null);
    bookings.saveAndFlush(b);
    notify(
        b,
        "Đơn #"
            + b.getId()
            + " ("
            + describe(b)
            + ") đã tự hủy vì quá "
            + AppConstants.DEPOSIT_HOLD_MINUTES
            + " phút chưa chuyển cọc. Khung giờ đã được trả lại, bạn có thể đặt lại.");
  }

  /** Hủy các đơn chờ cọc quá hạn giữ chỗ; gọi định kỳ bởi DepositHoldJob. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public int expireHolds() {
    int count = 0;
    for (Long id : bookings.expiredHoldIds(now())) {
      Booking b = lockBooking(id);
      // đọc lại sau khi khóa: khách có thể vừa báo chuyển cọc
      if (holdExpired(b)) {
        expireHold(b);
        count++;
      }
    }
    return count;
  }

  /** Tiền cọc theo phần trăm giá đơn, làm tròn lên hàng nghìn đồng. */
  public static BigDecimal depositFor(BigDecimal amount) {
    BigDecimal thousand = BigDecimal.valueOf(1000);
    return amount
        .multiply(BigDecimal.valueOf(AppConstants.DEPOSIT_PERCENT))
        .divide(BigDecimal.valueOf(100).multiply(thousand), 0, RoundingMode.CEILING)
        .multiply(thousand)
        .min(amount);
  }

  /**
   * Ảnh QR nhận cọc: ưu tiên ảnh chủ sân tự tải lên (khách tự nhập số tiền, nội dung), nếu không
   * thì tạo VietQR điền sẵn từ tài khoản ngân hàng; null nếu chủ sân chưa có cả hai.
   */
  public static String depositQrUrl(Booking b) {
    Account owner = b.getCourt().getOwner();
    if (b.getDepositAmount() == null) {
      return null;
    }
    if (owner.hasPaymentQr()) {
      return "/payment-qr/" + owner.getId() + "/" + owner.getPaymentQr();
    }
    if (!owner.hasBankAccount()) {
      return null;
    }
    java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
    return "https://img.vietqr.io/image/"
        + owner.getBankCode()
        + "-"
        + owner.getBankAccountNo()
        + "-compact2.png?amount="
        + b.getDepositAmount().toPlainString()
        + "&addInfo="
        + java.net.URLEncoder.encode(b.transferNote(), utf8)
        + "&accountName="
        + java.net.URLEncoder.encode(owner.getBankAccountName(), utf8);
  }

  private Booking lockBooking(Long id) {
    Long courtId =
        bookings.courtIdFor(id).orElseThrow(() -> new NotFoundException("Không tìm thấy đơn."));
    lock(courtId);
    return bookings.findById(id).orElseThrow();
  }

  private void requireCustomer(Booking b, Account actor) {
    if (!b.getCustomer().getId().equals(actor.getId())) {
      throw new AccessDeniedException("Bạn không được thao tác trên đơn này.");
    }
  }

  private void requireNotStarted(Booking b) {
    if (!b.getPlayDate().atTime(b.getStartHour(), 0).isAfter(now())) {
      throw new IllegalArgumentException("Khung giờ đã bắt đầu.");
    }
  }

  /** Bước 1: khách xác nhận chắc chắn đến sân, sau đó mới được chuyển cọc. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void commit(Long id, Account customer) {
    Booking b = lockBooking(id);
    requireCustomer(b, customer);
    if (!"PENDING_DEPOSIT".equals(b.getStatus()) || b.getCommittedAt() != null) {
      throw new IllegalArgumentException("Đơn không ở bước xác nhận đến sân.");
    }
    requireHoldActive(b);
    requireNotStarted(b);
    b.setCommittedAt(now());
    bookings.saveAndFlush(b);
  }

  /** Bước 2: khách báo đã chuyển khoản cọc, chờ chủ sân kiểm tra. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void reportDeposit(Long id, Account customer) {
    Booking b = lockBooking(id);
    requireCustomer(b, customer);
    if (!"PENDING_DEPOSIT".equals(b.getStatus())) {
      throw new IllegalArgumentException("Đơn không ở bước chờ đặt cọc.");
    }
    if (b.getCommittedAt() == null) {
      throw new IllegalArgumentException("Hãy xác nhận chắc chắn đến sân trước khi chuyển cọc.");
    }
    if (!b.getCourt().getOwner().canReceiveDeposit()) {
      throw new IllegalArgumentException("Chủ sân chưa cập nhật tài khoản nhận cọc.");
    }
    requireHoldActive(b);
    requireNotStarted(b);
    b.setStatus("DEPOSIT_SENT");
    b.setDepositSentAt(now());
    bookings.saveAndFlush(b);
  }

  /**
   * Bước 3: chủ sân kiểm tra tài khoản và xác nhận đã nhận cọc, khách được thông báo. Đơn khách
   * đã hủy (mất cọc) trước khi chủ sân kịp kiểm tra vẫn được xác nhận để ghi nhận vào doanh thu.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void confirmDeposit(Long id, Account owner) {
    Booking b = lockBooking(id);
    requireOwner(b.getCourt(), owner);
    if (!b.needsDepositCheck()) {
      throw new IllegalArgumentException("Đơn không có báo chuyển cọc cần xác nhận.");
    }
    b.setDepositConfirmedAt(now());
    if ("DEPOSIT_SENT".equals(b.getStatus())) {
      b.setStatus("CONFIRMED");
      notify(
          b,
          "Chủ sân đã nhận cọc "
              + money(b.getDepositAmount())
              + " cho đơn #"
              + b.getId()
              + " ("
              + describe(b)
              + "). Lịch chơi đã được xác nhận, còn "
              + money(b.remainingAmount())
              + " trả tại sân.");
    }
    bookings.saveAndFlush(b);
  }

  /** Chủ sân chưa thấy tiền về: đưa đơn về chờ đặt cọc để khách kiểm tra lại. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void rejectDeposit(Long id, Account owner) {
    Booking b = lockBooking(id);
    requireOwner(b.getCourt(), owner);
    if (!"DEPOSIT_SENT".equals(b.getStatus())) {
      throw new IllegalArgumentException("Đơn không có báo chuyển cọc cần xác nhận.");
    }
    b.setStatus("PENDING_DEPOSIT");
    b.setDepositSentAt(null);
    // cho khách thêm thời gian kiểm tra giao dịch và báo chuyển lại
    b.setHoldUntil(now().plusMinutes(AppConstants.DEPOSIT_HOLD_MINUTES));
    bookings.saveAndFlush(b);
    notify(
        b,
        "Chủ sân chưa nhận được tiền cọc cho đơn #"
            + b.getId()
            + " ("
            + describe(b)
            + "). Vui lòng kiểm tra lại giao dịch rồi báo chuyển cọc lại.");
  }

  private void notify(Booking b, String message) {
    Notification n = new Notification();
    n.setAccount(b.getCustomer());
    n.setBooking(b);
    n.setMessage(message.length() > 300 ? message.substring(0, 300) : message);
    n.setCreatedAt(now());
    notifications.save(n);
  }

  private static String describe(Booking b) {
    return String.format(
        "%s, %02d:00 %s",
        b.getCourt().getName(),
        b.getStartHour(),
        b.getPlayDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
  }

  private static String money(BigDecimal value) {
    return String.format(java.util.Locale.ROOT, "%,d", value.longValue()).replace(',', '.') + "đ";
  }

  @Transactional
  public List<Notification> recentNotifications(Account account) {
    return notifications.recentFor(account.getId());
  }

  @Transactional
  public void markNotificationsRead(Account account, Long bookingId) {
    notifications.markRead(account.getId(), bookingId, now());
  }

  @Transactional
  public void markAllNotificationsRead(Account account) {
    notifications.markAllRead(account.getId(), now());
  }

  /** Khách hủy sau khi đã báo chuyển cọc thì mất cọc, không hoàn tiền. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Booking cancel(Long id, Account actor) {
    Booking b = lockBooking(id);
    boolean owner = b.getCourt().getOwner().getId().equals(actor.getId());
    if (!owner && !b.getCustomer().getId().equals(actor.getId())) {
      throw new AccessDeniedException("Bạn không được hủy đơn này.");
    }
    if (!b.isActive()) {
      throw new IllegalArgumentException("Chỉ hủy đơn đang giữ khung giờ.");
    }
    LocalDateTime start = b.getPlayDate().atTime(b.getStartHour(), 0);
    int hours = owner ? 0 : AppConstants.CUSTOMER_CANCEL_HOURS;
    if (!start.isAfter(now().plusHours(hours))) {
      throw new IllegalArgumentException(
          owner
              ? "Không thể hủy sau khi giờ chơi bắt đầu."
              : "Khách hàng cần hủy trước giờ chơi trên 2 tiếng.");
    }
    b.setDepositForfeited(!owner && b.depositPaid());
    b.setStatus("CANCELLED");
    b.setActiveSlotKey(null);
    Booking saved = bookings.saveAndFlush(b);
    if (owner) {
      notify(
          b,
          "Chủ sân đã hủy đơn #"
              + b.getId()
              + " ("
              + describe(b)
              + ")."
              + (b.depositPaid()
                  ? " Chủ sân sẽ hoàn lại tiền cọc " + money(b.getDepositAmount()) + " cho bạn."
                  : ""));
    }
    return saved;
  }

  /**
   * Báo cáo theo ngày chơi. Doanh thu ghi nhận = tiền thuê của đơn hoàn tất + cọc giữ lại khi
   * khách hủy. Cọc của đơn chưa chơi chỉ là tiền đang giữ, chưa tính doanh thu vì có thể phải
   * hoàn nếu chủ sân hủy; khi đơn hoàn tất, cọc nằm trong tiền thuê.
   */
  public record RevenueReport(
      BigDecimal revenue,
      BigDecimal rentals,
      BigDecimal forfeited,
      BigDecimal heldDeposits,
      int completedCount,
      int forfeitedCount,
      int heldCount,
      java.util.Map<String, BigDecimal> byCourt) {

    public BigDecimal received() {
      return revenue.add(heldDeposits);
    }
  }

  @Transactional(readOnly = true)
  public RevenueReport revenueReport(Long ownerId, LocalDate from, LocalDate to, Long courtId) {
    BigDecimal rentals = BigDecimal.ZERO;
    BigDecimal forfeited = BigDecimal.ZERO;
    BigDecimal held = BigDecimal.ZERO;
    int completed = 0;
    int forfeitedCount = 0;
    int heldCount = 0;
    java.util.Map<String, BigDecimal> byCourt = new java.util.LinkedHashMap<>();
    for (Booking b : bookings.moneyForOwner(ownerId, from, to)) {
      if (courtId != null && !b.getCourt().getId().equals(courtId)) {
        continue;
      }
      BigDecimal earned;
      if ("COMPLETED".equals(b.getStatus())) {
        earned = b.getAmount();
        rentals = rentals.add(earned);
        completed++;
      } else if ("CANCELLED".equals(b.getStatus())) {
        // chủ sân hủy thì phải hoàn cọc, không tính
        if (!b.getDepositForfeited()) {
          continue;
        }
        earned = b.getDepositAmount();
        forfeited = forfeited.add(earned);
        forfeitedCount++;
      } else {
        continue;
      }
      byCourt.merge(
          b.getCourt().getName() + " (#" + b.getCourt().getId() + ")", earned, BigDecimal::add);
    }
    // Cọc đang giữ tính trên mọi đơn chưa chơi, không theo khoảng ngày
    for (Booking b : bookings.heldDepositsForOwner(ownerId)) {
      if (courtId == null || b.getCourt().getId().equals(courtId)) {
        held = held.add(b.getDepositAmount());
        heldCount++;
      }
    }
    return new RevenueReport(
        rentals.add(forfeited),
        rentals,
        forfeited,
        held,
        completed,
        forfeitedCount,
        heldCount,
        byCourt);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void complete(Long id, Account owner) {
    Booking b = lockBooking(id);
    requireOwner(b.getCourt(), owner);
    if (!"CONFIRMED".equals(b.getStatus())) {
      throw new IllegalArgumentException("Chỉ hoàn tất đơn đã xác nhận cọc.");
    }
    if (now().isBefore(b.getPlayDate().atStartOfDay().plusHours(b.getStartHour() + 1))) {
      throw new IllegalArgumentException("Chỉ hoàn tất sau khi khung giờ kết thúc và đã thu tiền.");
    }
    b.setStatus("COMPLETED");
    bookings.saveAndFlush(b);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void toggleBlock(Long id, LocalDate date, int hour, Account owner) {
    Court c = lock(id);
    requireOwner(c, owner);
    futureSlot(c, date, hour);
    String slotKey = key(id, date, hour);
    Optional<BlockedSlot> old = blocks.findBySlotKey(slotKey);
    if (old.isPresent()) {
      blocks.delete(old.get());
      return;
    }
    if (bookings.existsByActiveSlotKey(slotKey)) {
      throw new IllegalArgumentException("Không thể khóa khung giờ đã có đơn.");
    }
    BlockedSlot b = new BlockedSlot();
    b.setCourt(c);
    b.setPlayDate(date);
    b.setStartHour(hour);
    b.setSlotKey(slotKey);
    blocks.saveAndFlush(b);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Court saveCourt(Long id, CourtForm f, Account requestedOwner, List<MultipartFile> uploads) {
    Account owner=entityManager.find(Account.class,requestedOwner.getId(),jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    if (owner==null || owner.isLocked() || !owner.isOwner()) {
      throw new AccessDeniedException("Chỉ chủ sân được quản lý sân.");
    }
    if (f.getOpenHour() >= f.getCloseHour()) {
      throw new IllegalArgumentException("Giờ đóng cửa phải sau giờ mở cửa.");
    }
    if (!AppConstants.AREAS.contains(f.getArea())) {
      throw new IllegalArgumentException("Chọn khu vực hợp lệ.");
    }
    boolean peak =
        f.getPeakStart() != null || f.getPeakEnd() != null || f.getPeakPrice() != null;
    if (peak) {
      if (f.getPeakStart() == null || f.getPeakEnd() == null || f.getPeakPrice() == null) {
        throw new IllegalArgumentException("Nhập đủ giờ bắt đầu, giờ kết thúc và giá cao điểm.");
      }
      if (f.getPeakStart() >= f.getPeakEnd()) {
        throw new IllegalArgumentException("Khung giá cao điểm không hợp lệ.");
      }
      if (f.getPeakStart() < f.getOpenHour() || f.getPeakEnd() > f.getCloseHour()) {
        throw new IllegalArgumentException("Khung giá cao điểm phải nằm trong giờ mở cửa.");
      }
    }
    Court c = id == null ? new Court() : lock(id);
    if (id != null) {
      requireOwner(c, owner);
      boolean conflict =
          bookings.findByCourtOwnerIdOrderByCreatedAtDesc(owner.getId()).stream()
              .anyMatch(
                  b ->
                      b.getCourt().getId().equals(id)
                          && b.isActive()
                          && !b.getPlayDate().isBefore(today())
                          && (b.getStartHour() < f.getOpenHour()
                              || b.getStartHour() >= f.getCloseHour()));
      if (conflict) {
        throw new IllegalArgumentException(
            "Giờ mở cửa mới loại bỏ khung giờ có đơn. Hãy xử lý đơn trước.");
      }
    }
    c.setName(f.getName().trim());
    c.setSport(f.getSport());
    c.setArea(f.getArea());
    c.setAddress(f.getAddress().trim());
    c.setDescription(f.getDescription());
    c.setAmenities(f.getAmenities());
    c.setPrice(f.getPrice());
    c.setOpenHour(f.getOpenHour());
    c.setCloseHour(f.getCloseHour());
    c.setActive(f.getActive());
    c.setOwner(owner);
    c.getPriceRules().clear();
    BigDecimal min = f.getPrice();
    if (peak) {
      PriceRule rule = new PriceRule();
      rule.setCourt(c);
      rule.setStartHour(f.getPeakStart());
      rule.setEndHour(f.getPeakEnd());
      rule.setPrice(f.getPeakPrice());
      c.getPriceRules().add(rule);
      min = min.min(f.getPeakPrice());
    }
    c.setMinPrice(min);
    Court saved = courts.saveAndFlush(c);
    addImages(saved, owner, uploads);
    return saved;
  }

  @Transactional
  public void addImages(Court court, Account owner, List<MultipartFile> uploads) {
    requireOwner(court, owner);
    if (uploads == null) {
      return;
    }
    int sort = (int) images.countByCourtId(court.getId());
    for (MultipartFile file : uploads) {
      if (file == null || file.isEmpty()) {
        continue;
      }
      if (sort >= AppConstants.MAX_IMAGES) {
        throw new IllegalArgumentException("Mỗi sân tối đa 8 ảnh.");
      }
      ImageStorageService.StoredImage stored = storage.save(file);
      storage.write(court.getId(), stored.storedName(), file);
      CourtImage img = new CourtImage();
      img.setCourt(court);
      img.setStoredName(stored.storedName());
      img.setContentType(stored.contentType());
      img.setSortOrder(sort++);
      images.save(img);
    }
  }

  @Transactional
  public void deleteImage(Long courtId, Long imageId, Account owner) {
    Court court = requireCourt(courtId);
    requireOwner(court, owner);
    CourtImage img =
        images
            .findByIdAndCourtId(imageId, courtId)
            .orElseThrow(() -> new NotFoundException("Không tìm thấy ảnh."));
    storage.delete(courtId, img.getStoredName());
    images.delete(img);
  }
}
