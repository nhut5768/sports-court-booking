package vn.uth.court;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"app.demo.seed=false", "app.upload-dir=${java.io.tmpdir}/san-nha-tests"})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class CourtIntegrationTest {
  @Autowired AccountRepository accounts;
  @Autowired CourtRepository courts;
  @Autowired BookingRepository bookings;
  @Autowired BlockedSlotRepository blocks;
  @Autowired PriceRuleRepository prices;
  @Autowired CourtImageRepository images;
  @Autowired NotificationRepository notifications;
  @Autowired BookingService service;
  @Autowired MockMvc mvc;
  @Autowired ReviewRepository reviewRepo;
  @Autowired ReviewService reviewService;
  @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
  @Autowired AccountService accountService;
  @Autowired org.springframework.security.core.userdetails.UserDetailsService userDetails;
  Account owner, guest, other, secondOwner;
  Court court;
  LocalDate day;

  @BeforeEach
  void setup() {
    reviewRepo.deleteAll();
    notifications.deleteAll();
    images.deleteAll();
    prices.deleteAll();
    blocks.deleteAll();
    bookings.deleteAll();
    courts.deleteAll();
    accounts.deleteAll();
    owner = account("owner@test.vn", "0900000001", "OWNER");
    secondOwner = account("owner2@test.vn", "0900000002", "OWNER");
    guest = account("guest@test.vn", "0900000003", "CUSTOMER");
    other = account("other@test.vn", "0900000004", "CUSTOMER");
    court = newCourt(owner, "Sân kiểm thử", "120000");
    day = service.today().plusDays(1);
  }

  Account account(String email, String phone, String role) {
    Account a = new Account();
    a.setName(email);
    a.setEmail(email);
    a.setPhone(phone);
    a.setPassword("unused");
    a.setRole(role);
    return accounts.saveAndFlush(a);
  }

  Court newCourt(Account own, String name, String price) {
    Court c = new Court();
    c.setName(name);
    c.setSport("Cầu lông");
    c.setArea("Bình Thạnh");
    c.setAddress("TP.HCM");
    c.setDescription("Mô tả sân");
    c.setAmenities("Đèn, nhà vệ sinh");
    c.setPrice(new BigDecimal(price));
    c.setMinPrice(new BigDecimal(price));
    c.setOpenHour(6);
    c.setCloseHour(22);
    c.setActive(true);
    c.setOwner(own);
    return courts.saveAndFlush(c);
  }

  @Test
  void priceSnapshotAndDuplicate() {
    Booking b = service.book(court.getId(), day, 18, guest);
    assertThat(b.getAmount()).isEqualByComparingTo("120000");
    court.setPrice(new BigDecimal("200000"));
    court.setMinPrice(new BigDecimal("200000"));
    courts.saveAndFlush(court);
    assertThat(bookings.findById(b.getId()).orElseThrow().getAmount())
        .isEqualByComparingTo("120000");
    assertThatThrownBy(() -> service.book(court.getId(), day, 18, other))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void peakPriceIsSnapshotted() {
    PriceRule rule = new PriceRule();
    rule.setCourt(court);
    rule.setStartHour(17);
    rule.setEndHour(22);
    rule.setPrice(new BigDecimal("180000"));
    prices.saveAndFlush(rule);
    Booking b = service.book(court.getId(), day, 18, guest);
    assertThat(b.getAmount()).isEqualByComparingTo("180000");
    rule.setPrice(new BigDecimal("300000"));
    prices.saveAndFlush(rule);
    assertThat(bookings.findById(b.getId()).orElseThrow().getAmount())
        .isEqualByComparingTo("180000");
    Booking morning = service.book(court.getId(), day, 9, guest);
    assertThat(morning.getAmount()).isEqualByComparingTo("120000");
  }

  void giveOwnerBank() {
    owner.setBankCode("VCB");
    owner.setBankAccountNo("0123456789");
    owner.setBankAccountName("CHU SAN");
    accounts.saveAndFlush(owner);
  }

  @Test
  void depositIsHalfRoundedUpToThousand() {
    assertThat(BookingService.depositFor(new BigDecimal("120000"))).isEqualByComparingTo("60000");
    assertThat(BookingService.depositFor(new BigDecimal("185000"))).isEqualByComparingTo("93000");
    Booking b = service.book(court.getId(), day, 18, guest);
    assertThat(b.getStatus()).isEqualTo("PENDING_DEPOSIT");
    assertThat(b.getDepositAmount()).isEqualByComparingTo("60000");
  }

  @Test
  void depositRequiresCommitmentFirst() {
    giveOwnerBank();
    Booking b = service.book(court.getId(), day, 18, guest);
    assertThatThrownBy(() -> service.reportDeposit(b.getId(), guest))
        .isInstanceOf(IllegalArgumentException.class);
    service.commit(b.getId(), guest);
    assertThatThrownBy(() -> service.commit(b.getId(), other))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    service.reportDeposit(b.getId(), guest);
    assertThat(bookings.findById(b.getId()).orElseThrow().getStatus()).isEqualTo("DEPOSIT_SENT");
  }

  @Test
  void depositNeedsOwnerBankAccount() {
    Booking b = service.book(court.getId(), day, 18, guest);
    service.commit(b.getId(), guest);
    assertThatThrownBy(() -> service.reportDeposit(b.getId(), guest))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ownerConfirmsOrRejectsDeposit() {
    giveOwnerBank();
    Booking b = service.book(court.getId(), day, 18, guest);
    service.commit(b.getId(), guest);
    service.reportDeposit(b.getId(), guest);
    assertThatThrownBy(() -> service.confirmDeposit(b.getId(), secondOwner))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    service.rejectDeposit(b.getId(), owner);
    assertThat(bookings.findById(b.getId()).orElseThrow().getStatus()).isEqualTo("PENDING_DEPOSIT");
    service.reportDeposit(b.getId(), guest);
    service.confirmDeposit(b.getId(), owner);
    Booking saved = bookings.findById(b.getId()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo("CONFIRMED");
    assertThat(saved.getDepositConfirmedAt()).isNotNull();
  }

  @Test
  void customerCancelAfterDepositForfeitsIt() {
    giveOwnerBank();
    Booking b = service.book(court.getId(), day, 18, guest);
    service.commit(b.getId(), guest);
    service.reportDeposit(b.getId(), guest);
    service.confirmDeposit(b.getId(), owner);
    Booking cancelled = service.cancel(b.getId(), guest);
    assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
    assertThat(cancelled.getDepositForfeited()).isTrue();
    assertThat(service.book(court.getId(), day, 18, other)).isNotNull();
  }

  @Test
  void cancelBeforeDepositOrByOwnerKeepsDeposit() {
    giveOwnerBank();
    Booking early = service.book(court.getId(), day, 17, guest);
    service.commit(early.getId(), guest);
    assertThat(service.cancel(early.getId(), guest).getDepositForfeited()).isFalse();
    Booking paid = service.book(court.getId(), day, 18, guest);
    service.commit(paid.getId(), guest);
    service.reportDeposit(paid.getId(), guest);
    assertThat(service.cancel(paid.getId(), owner).getDepositForfeited()).isFalse();
  }

  @Test
  void professionalUiContractsAndPreview() throws Exception {
    giveOwnerBank();
    CourtImage photo = new CourtImage();
    photo.setCourt(court);
    photo.setStoredName("ui-preview.png");
    photo.setContentType("image/png");
    images.saveAndFlush(photo);
    CourtImage sidePhoto = new CourtImage();sidePhoto.setCourt(court);sidePhoto.setStoredName("ui-preview-side.png");sidePhoto.setContentType("image/png");sidePhoto.setSortOrder(1);images.saveAndFlush(sidePhoto);
    preview("empty-bookings", get("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")));
    preview("empty-owner", get("/owner").with(user(secondOwner.getEmail()).roles("OWNER")));
    preview("login-error", get("/login").param("error", ""));
    Booking b = service.book(court.getId(), day, 18, guest);
    String home = preview("home", get("/"));
    assertThat(home).contains("how-it-works", "/vendor/san-nha-ui.js", "Nhóm 2");
    assertThat(home).doesNotContain("account-menu-panel", "fonts.googleapis.com");
    String filtered = preview("filtered", get("/").param("sport", "Cầu lông").param("minPrice", "100000"));
    assertThat(filtered).contains("value=\"100000\"");
    assertThat(filtered).containsPattern(
        "(?s)<input\\b(?=[^>]*\\bid=\"hero-sport\")(?=[^>]*\\bvalue=\"Cầu lông\")(?=[^>]*\\blist=\"search-sport-options\")[^>]*>");
    assertThat(filtered).containsPattern(
        "(?s)<datalist\\b[^>]*\\bid=\"search-sport-options\"[^>]*>.*?</datalist>");
    preview("login", get("/login"));
    preview("register", get("/register"));
    preview("detail", get("/courts/" + court.getId()).param("date", day.toString()));
    preview("confirm", get("/bookings/confirm").param("courtId", court.getId().toString())
        .param("date", day.toString()).param("hour", "19").with(user(guest.getEmail()).roles("CUSTOMER")));
    String history = preview("bookings", get("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")));
    assertThat(history).contains("id=\"notifications\"", "Giữ đến", "Xem và đặt cọc");
    String pending = preview("pending", get("/bookings/" + b.getId()).with(user(guest.getEmail()).roles("CUSTOMER")));
    assertThat(pending).doesNotContain("data-copy-target", "Đã nhận cọc</button>");
    service.commit(b.getId(), guest);
    String transfer = preview("transfer", get("/bookings/" + b.getId()).with(user(guest.getEmail()).roles("CUSTOMER")));
    assertThat(transfer).contains("data-copy-target=\"transfer-account\"", "data-copy-target=\"transfer-note\"", "data-copy-target=\"transfer-amount\"");
    assertThat(transfer).contains("name=\"_csrf\"", "Còn lại trả tại sân");
    service.reportDeposit(b.getId(), guest);
    String ownerPage = preview("owner", get("/owner").param("to", day.toString()).with(user(owner.getEmail()).roles("OWNER")));
    assertThat(ownerPage).contains("needs-deposit-check", "Kiểm tra cọc", "1 đơn báo đã chuyển cọc");
    assertThat(ownerPage).doesNotContain("#notifications");
    preview("owner-order", get("/owner/bookings/" + b.getId()).with(user(owner.getEmail()).roles("OWNER")));
    preview("court-form", get("/owner/courts/" + court.getId() + "/edit").with(user(owner.getEmail()).roles("OWNER")));
    preview("account", get("/account").with(user(owner.getEmail()).roles("OWNER")));
    preview("password", get("/account/password").with(user(guest.getEmail()).roles("CUSTOMER")));
    mvc.perform(get("/vendor/san-nha-ui.js")).andExpect(status().isOk());
    mvc.perform(get("/css/app.css")).andExpect(status().isOk());
  }

  private String preview(String name, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
    String html = mvc.perform(request).andExpect(status().isOk()).andReturn()
        .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    if (Boolean.getBoolean("sanNha.uiSnapshots")) {
      java.nio.file.Path folder = java.nio.file.Path.of("target", "ui-preview");
      java.nio.file.Files.createDirectories(folder);
      java.nio.file.Files.writeString(folder.resolve(name + ".html"), html, java.nio.charset.StandardCharsets.UTF_8);
    }
    return html;
  }
  @Test
  void depositPagesRender() throws Exception {
    giveOwnerBank();
    Booking b = service.book(court.getId(), day, 18, guest);
    mvc.perform(get("/bookings/" + b.getId()).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Xác nhận chắc chắn đến sân")))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("img.vietqr.io"))));
    service.commit(b.getId(), guest);
    mvc.perform(get("/bookings/" + b.getId()).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("img.vietqr.io/image/VCB-0123456789")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("SANNHA " + b.getId())));
    service.reportDeposit(b.getId(), guest);
    mvc.perform(get("/owner/bookings/" + b.getId()).with(user(owner.getEmail()).roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Đã nhận cọc")));
    mvc.perform(get("/owner").with(user(owner.getEmail()).roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("1 đơn báo đã chuyển cọc")));
    mvc.perform(
            post("/account/bank")
                .with(user(owner.getEmail()).roles("OWNER"))
                .with(csrf())
                .param("bankCode", "MB")
                .param("bankAccountNo", "9999 8888 77")
                .param("bankAccountName", "Nguyễn Văn Đức"))
        .andExpect(status().is3xxRedirection());
    Account saved = accounts.findById(owner.getId()).orElseThrow();
    assertThat(saved.getBankAccountNo()).isEqualTo("9999888877");
    assertThat(saved.getBankAccountName()).isEqualTo("NGUYEN VAN DUC");
  }

  @Test
  void customerIsNotifiedWhenOwnerHandlesDeposit() throws Exception {
    giveOwnerBank();
    Booking b = service.book(court.getId(), day, 18, guest);
    service.commit(b.getId(), guest);
    service.reportDeposit(b.getId(), guest);
    service.rejectDeposit(b.getId(), owner);
    service.reportDeposit(b.getId(), guest);
    service.confirmDeposit(b.getId(), owner);
    assertThat(notifications.countByAccountIdAndReadAtIsNull(guest.getId())).isEqualTo(2);
    assertThat(service.recentNotifications(guest).get(0).getMessage())
        .contains("đã nhận cọc 60.000đ", "#" + b.getId());
    mvc.perform(get("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("đã nhận cọc 60.000đ")));
    mvc.perform(get("/bookings/" + b.getId()).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk());
    assertThat(notifications.countByAccountIdAndReadAtIsNull(guest.getId())).isZero();
  }

  @Test
  void revenueCountsForfeitedAndHeldDeposits() throws Exception {
    giveOwnerBank();
    Booking held = service.book(court.getId(), day, 18, guest);
    service.commit(held.getId(), guest);
    service.reportDeposit(held.getId(), guest);
    service.confirmDeposit(held.getId(), owner);
    Booking lost = service.book(court.getId(), day, 19, guest);
    service.commit(lost.getId(), guest);
    service.reportDeposit(lost.getId(), guest);
    service.cancel(lost.getId(), guest);
    // khách hủy trước khi chủ sân kịp xác nhận: chưa tính cho đến khi chủ sân xác nhận tiền về
    assertThat(service.revenueReport(owner.getId(), day, day, null).forfeited()).isZero();
    service.confirmDeposit(lost.getId(), owner);
    Booking refunded = service.book(court.getId(), day, 20, guest);
    service.commit(refunded.getId(), guest);
    service.reportDeposit(refunded.getId(), guest);
    service.confirmDeposit(refunded.getId(), owner);
    service.cancel(refunded.getId(), owner);
    BookingService.RevenueReport r = service.revenueReport(owner.getId(), day, day, null);
    assertThat(r.forfeited()).isEqualByComparingTo("60000");
    assertThat(r.revenue()).isEqualByComparingTo("60000");
    assertThat(r.heldDeposits()).isEqualByComparingTo("60000");
    assertThat(r.received()).isEqualByComparingTo("120000");
    mvc.perform(
            get("/owner")
                .with(user(owner.getEmail()).roles("OWNER"))
                .param("from", day.toString())
                .param("to", day.toString()))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Cọc giữ lại · 1 đơn khách hủy")));
  }

  @Test
  void uploadedQrIsShownToCustomer() throws Exception {
    byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    mvc.perform(
            multipart("/account/payment-qr")
                .file(new MockMultipartFile("qr", "qr.png", "image/png", png))
                .with(user(owner.getEmail()).roles("OWNER"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection());
    Account saved = accounts.findById(owner.getId()).orElseThrow();
    assertThat(saved.hasPaymentQr()).isTrue();
    Booking b = service.book(court.getId(), day, 18, guest);
    service.commit(b.getId(), guest);
    String qrPath = "/payment-qr/" + owner.getId() + "/" + saved.getPaymentQr();
    mvc.perform(get("/bookings/" + b.getId()).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString(qrPath)));
    mvc.perform(get(qrPath).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk());
    service.reportDeposit(b.getId(), guest);
    assertThat(bookings.findById(b.getId()).orElseThrow().getStatus()).isEqualTo("DEPOSIT_SENT");
  }

  @Test
  void phonesAreNormalizedToTenDigitVietnameseMobile() {
    assertThat(Phones.normalize("+84 912 345 678")).isEqualTo("0912345678");
    assertThat(Phones.normalize("84912345678")).isEqualTo("0912345678");
    assertThat(Phones.normalize("0912.345.678")).isEqualTo("0912345678");
    assertThat(Phones.valid("0912345678")).isTrue();
    assertThat(Phones.valid("0212345678")).isFalse();
    assertThat(Phones.valid("091234567")).isFalse();
    assertThat(Phones.valid("09123456789")).isFalse();
  }

  @Test
  void loginWorksWithEmailOrPhone() {
    assertThat(userDetails.loadUserByUsername("0900000003").getUsername()).isEqualTo(guest.getEmail());
    assertThat(userDetails.loadUserByUsername("+84 900 000 003").getUsername())
        .isEqualTo(guest.getEmail());
    assertThat(userDetails.loadUserByUsername("GUEST@test.vn").getUsername()).isEqualTo(guest.getEmail());
    assertThatThrownBy(() -> userDetails.loadUserByUsername("0999999999"))
        .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
  }

  @Test
  void registrationRequiresUniqueVietnamesePhone() throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("name", "Sai số")
                .param("email", "bad@test.vn")
                .param("phone", "123456789")
                .param("password", "Password123"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("10 số")));
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("name", "Trùng số")
                .param("email", "dup@test.vn")
                .param("phone", "0900 000 003")
                .param("password", "Password123"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Số điện thoại đã được đăng ký")));
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("name", "Đúng số")
                .param("email", "ok@test.vn")
                .param("phone", "+84 912 345 678")
                .param("password", "Password123"))
        .andExpect(status().is3xxRedirection());
    assertThat(accounts.findByEmail("ok@test.vn").orElseThrow().getPhone()).isEqualTo("0912345678");
  }

  @Test
  void bookingNeedsPhone() {
    guest.setPhone(null);
    accounts.saveAndFlush(guest);
    assertThatThrownBy(() -> service.book(court.getId(), day, 18, guest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("số điện thoại");
    accountService.updatePhone(guest.getEmail(), "0911 222 333");
    assertThatThrownBy(() -> accountService.updatePhone(other.getEmail(), "0911222333"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.book(court.getId(), day, 18, accounts.findById(guest.getId()).orElseThrow()))
        .isNotNull();
  }

  @Test
  Booking completedBooking(Account customer, int hour) {
    Booking b = service.book(court.getId(), day, hour, customer);
    b.setPlayDate(service.today().minusDays(1));
    b.setStatus("CONFIRMED");
    bookings.saveAndFlush(b);
    service.complete(b.getId(), owner);
    return bookings.findById(b.getId()).orElseThrow();
  }

  @Test
  void onlyCompletedBookingOwnerCanReview() {
    Booking pending = service.book(court.getId(), day, 18, guest);
    assertThatThrownBy(() -> reviewService.save(pending.getId(), guest, 5, "Tốt"))
        .isInstanceOf(IllegalArgumentException.class);
    Booking done = completedBooking(guest, 16);
    assertThatThrownBy(() -> reviewService.save(done.getId(), other, 5, "Không phải đơn của tôi"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> reviewService.save(done.getId(), guest, 6, null))
        .isInstanceOf(IllegalArgumentException.class);
    reviewService.save(done.getId(), guest, 4, "  Sân sạch  ");
    // gửi lại thì cập nhật, không tạo đánh giá thứ hai
    Review updated = reviewService.save(done.getId(), guest, 5, "");
    assertThat(reviewRepo.count()).isEqualTo(1);
    assertThat(updated.getRating()).isEqualTo(5);
    assertThat(updated.getComment()).isNull();
  }

  @Test
  void ratingsAreShownOnCourtAndHomePages() throws Exception {
    reviewService.save(completedBooking(guest, 15).getId(), guest, 5, "Đèn sáng, mặt sân tốt");
    reviewService.save(completedBooking(other, 16).getId(), other, 4, null);
    ReviewService.Rating rating = reviewService.rating(court.getId());
    assertThat(rating.total()).isEqualTo(2);
    assertThat(rating.averageLabel()).isEqualTo("4,5");
    mvc.perform(get("/courts/" + court.getId()))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Đèn sáng, mặt sân tốt")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("(2 đánh giá)")));
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("4,5")));
  }

  @Test
  void reviewFormOnCompletedBooking() throws Exception {
    Booking done = completedBooking(guest, 16);
    mvc.perform(get("/bookings/" + done.getId()).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Gửi đánh giá")));
    mvc.perform(
            post("/bookings/" + done.getId() + "/review")
                .with(user(guest.getEmail()).roles("CUSTOMER"))
                .with(csrf())
                .param("rating", "3")
                .param("comment", "Ổn"))
        .andExpect(status().is3xxRedirection());
    assertThat(reviewService.forBooking(done.getId()).orElseThrow().getRating()).isEqualTo(3);
    mvc.perform(get("/owner/bookings/" + done.getId()).with(user(owner.getEmail()).roles("OWNER")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Đánh giá của khách")));
  }

  void expireHoldOf(Booking b) {
    Booking saved = bookings.findById(b.getId()).orElseThrow();
    saved.setHoldUntil(service.now().minusMinutes(1));
    bookings.saveAndFlush(saved);
  }

  @Test
  void unpaidBookingIsAutoCancelledAfterHold() {
    Booking b = service.book(court.getId(), day, 18, guest);
    assertThat(b.getHoldUntil()).isAfter(service.now().plusMinutes(29));
    expireHoldOf(b);
    assertThat(service.expireHolds()).isEqualTo(1);
    Booking cancelled = bookings.findById(b.getId()).orElseThrow();
    assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
    assertThat(cancelled.getActiveSlotKey()).isNull();
    assertThat(service.recentNotifications(guest).get(0).getMessage()).contains("tự hủy");
    assertThat(service.book(court.getId(), day, 18, other)).isNotNull();
  }

  @Test
  void expiredHoldIsReleasedWhenSomeoneElseBooks() {
    Booking b = service.book(court.getId(), day, 18, guest);
    expireHoldOf(b);
    // tác vụ định kỳ chưa chạy nhưng người khác vẫn đặt được
    assertThat(service.book(court.getId(), day, 18, other)).isNotNull();
    assertThat(bookings.findById(b.getId()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
  }

  @Test
  void expiredHoldCannotReportDepositAndPaidBookingsAreKept() {
    giveOwnerBank();
    Booking late = service.book(court.getId(), day, 17, guest);
    service.commit(late.getId(), guest);
    expireHoldOf(late);
    assertThatThrownBy(() -> service.reportDeposit(late.getId(), guest))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("quá");
    Booking sent = service.book(court.getId(), day, 18, guest);
    service.commit(sent.getId(), guest);
    service.reportDeposit(sent.getId(), guest);
    expireHoldOf(sent);
    service.expireHolds();
    assertThat(bookings.findById(sent.getId()).orElseThrow().getStatus()).isEqualTo("DEPOSIT_SENT");
    // chủ sân báo chưa nhận tiền thì khách có thêm 30 phút
    service.rejectDeposit(sent.getId(), owner);
    assertThat(bookings.findById(sent.getId()).orElseThrow().getHoldUntil())
        .isAfter(service.now().plusMinutes(29));
  }

  @Test
  void ownerIssuesTemporaryPasswordAndCustomerMustChangeIt() throws Exception {
    Booking b = service.book(court.getId(), day, 18, guest);
    assertThatThrownBy(() -> accountService.issueTemporaryPassword(secondOwner.getEmail(), guest.getId()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> accountService.issueTemporaryPassword(owner.getEmail(), other.getId()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    mvc.perform(
            post("/owner/bookings/" + b.getId() + "/temp-password")
                .with(user(owner.getEmail()).roles("OWNER"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("tempPassword"));
    String temp = accountService.issueTemporaryPassword(owner.getEmail(), guest.getId());
    assertThat(temp).hasSize(10);
    Account reset = accounts.findById(guest.getId()).orElseThrow();
    assertThat(reset.getMustChangePassword()).isTrue();
    assertThat(passwords.matches(temp, reset.getPassword())).isTrue();
    // chưa đổi mật khẩu thì mọi trang đều chuyển về trang đổi mật khẩu
    mvc.perform(get("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(redirectedUrlPattern("/account/password*"));
    mvc.perform(get("/account/password").with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("mật khẩu tạm")));
    mvc.perform(
            post("/account/password")
                .with(user(guest.getEmail()).roles("CUSTOMER"))
                .with(csrf())
                .param("current", temp)
                .param("password", "MatKhauMoi123")
                .param("confirm", "MatKhauMoi123"))
        .andExpect(redirectedUrlPattern("/account*"));
    Account changed = accounts.findById(guest.getId()).orElseThrow();
    assertThat(changed.getMustChangePassword()).isFalse();
    assertThat(passwords.matches("MatKhauMoi123", changed.getPassword())).isTrue();
    mvc.perform(get("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk());
  }

  @Test
  void changePasswordNeedsCorrectCurrentPassword() {
    guest.setPassword(passwords.encode("MatKhauCu123"));
    accounts.saveAndFlush(guest);
    assertThatThrownBy(
            () -> accountService.changePassword(guest.getEmail(), "sai", "MatKhauMoi123", "MatKhauMoi123"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> accountService.changePassword(guest.getEmail(), "MatKhauCu123", "MatKhauMoi123", "khac"))
        .isInstanceOf(IllegalArgumentException.class);
    accountService.changePassword(guest.getEmail(), "MatKhauCu123", "MatKhauMoi123", "MatKhauMoi123");
    assertThat(passwords.matches("MatKhauMoi123", accounts.findById(guest.getId()).orElseThrow().getPassword()))
        .isTrue();
  }

  @Test
  void ownerRepliesToReviewAndCustomerIsNotified() throws Exception {
    Review review = reviewService.save(completedBooking(guest, 15).getId(), guest, 4, "Sân tốt");
    assertThatThrownBy(() -> reviewService.reply(review.getId(), secondOwner, "Không phải sân tôi"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    mvc.perform(
            post("/owner/reviews/" + review.getId() + "/reply")
                .with(user(owner.getEmail()).roles("OWNER"))
                .with(csrf())
                .param("reply", "Cảm ơn bạn, hẹn gặp lại!"))
        .andExpect(redirectedUrlPattern("/courts/" + court.getId() + "*"));
    assertThat(service.recentNotifications(guest).get(0).getMessage()).contains("trả lời đánh giá");
    mvc.perform(get("/courts/" + court.getId()))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Cảm ơn bạn, hẹn gặp lại!")));
    // sửa phản hồi không gửi thêm thông báo; để trống thì xóa phản hồi
    reviewService.reply(review.getId(), owner, "Sửa lại");
    reviewService.reply(review.getId(), owner, "  ");
    assertThat(reviewRepo.findById(review.getId()).orElseThrow().getOwnerReply()).isNull();
    assertThat(notifications.countByAccountIdAndReadAtIsNull(guest.getId())).isEqualTo(1);
  }

  @Test
  void cancellationReleasesSlot() {
    Booking b = service.book(court.getId(), day, 18, guest);
    service.cancel(b.getId(), guest);
    assertThat(bookings.findById(b.getId()).orElseThrow().getStatus()).isEqualTo("CANCELLED");
    assertThat(service.book(court.getId(), day, 18, other)).isNotNull();
  }

  @Test
  void cannotCancelAnotherCustomersBooking() {
    Booking b = service.book(court.getId(), day, 18, guest);
    assertThatThrownBy(() -> service.cancel(b.getId(), other))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void cannotViewAnotherCustomersBooking() {
    Booking b = service.book(court.getId(), day, 18, guest);
    assertThatThrownBy(() -> service.requireOwnedBooking(b.getId(), other))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void lockAndUnlockSlot() {
    service.toggleBlock(court.getId(), day, 18, owner);
    assertThatThrownBy(() -> service.book(court.getId(), day, 18, guest))
        .isInstanceOf(IllegalArgumentException.class);
    service.toggleBlock(court.getId(), day, 18, owner);
    assertThat(service.book(court.getId(), day, 18, guest)).isNotNull();
  }

  @Test
  void onlyOwnerMayBlock() {
    assertThatThrownBy(() -> service.toggleBlock(court.getId(), day, 18, other))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> service.toggleBlock(court.getId(), day, 18, secondOwner))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void dateAndHoursAreValidated() {
    assertThatThrownBy(() -> service.book(court.getId(), service.today().minusDays(1), 18, guest))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.book(court.getId(), day, 23, guest))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.book(court.getId(), service.today().plusDays(31), 18, guest))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void inactiveCourtCannotBeBooked() {
    court.setActive(false);
    courts.saveAndFlush(court);
    assertThatThrownBy(() -> service.book(court.getId(), day, 18, guest))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ownerAccountCannotBook() {
    assertThatThrownBy(() -> service.book(court.getId(), day, 18, owner))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void cannotCompleteFutureBooking() {
    Booking b = service.book(court.getId(), day, 18, guest);
    assertThatThrownBy(() -> service.complete(b.getId(), owner))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void concurrentBookingsOnlyOneWins() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch gate = new CountDownLatch(1);
    Callable<Boolean> action =
        () -> {
          gate.await();
          try {
            service.book(court.getId(), day, 18, guest);
            return true;
          } catch (IllegalArgumentException
              | org.springframework.dao.DataIntegrityViolationException e) {
            return false;
          }
        };
    try {
      Future<Boolean> a = pool.submit(action);
      Future<Boolean> b = pool.submit(action);
      gate.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(bookings.count()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void discoveryDateUsesRealAvailabilityAndKeepsSelectedDate() throws Exception {
    court.setOpenHour(18);court.setCloseHour(20);courts.saveAndFlush(court);
    service.book(court.getId(),day,19,guest);
    mvc.perform(get("/").param("date",day.toString()))
        .andExpect(status().isOk())
        .andExpect(model().attribute("availability",java.util.Map.of(court.getId(),1L)))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/courts/"+court.getId()+"?date="+day)))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("1 khung giờ còn trống trong ngày")));
    mvc.perform(get("/").param("date",service.today().minusDays(1).toString())).andExpect(status().isBadRequest());
    preview("dated",get("/").param("date",day.toString()));
  }

  @Test
  void publicPagesRender() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Sân kiểm thử")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa có ảnh sân")));
    mvc.perform(get("/courts/" + court.getId()).param("date", day.toString()))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Chủ sân chưa tải ảnh")));
    mvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Đăng nhập")));
    mvc.perform(get("/register")).andExpect(status().isOk());
    mvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("UP")));
    mvc.perform(get("/courts/99999"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Không tìm thấy")));
  }

  @Test
  void combinedSearchNormalizesFiltersAndReturnsOnlyMatchingActiveCourts() throws Exception {
    newCourt(owner, "Sân giá cao", "250000");
    Court otherArea = newCourt(owner, "Sân khu vực khác", "120000");
    otherArea.setArea("Thủ Đức");courts.saveAndFlush(otherArea);
    Court otherSport = newCourt(owner, "Sân môn khác", "120000");
    otherSport.setSport("Tennis");courts.saveAndFlush(otherSport);
    Court inactive = newCourt(owner, "Sân tạm đóng", "120000");
    inactive.setActive(false);courts.saveAndFlush(inactive);
    var result = mvc.perform(get("/").param("q", "  Sân  ")
        .param("sport", "  cầu lông  ").param("area", "  bình thạnh  ")
        .param("date", day.toString()).param("minPrice", " 120000 ").param("maxPrice", "120000"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("q", "Sân"))
        .andExpect(model().attribute("sport", "Cầu lông"))
        .andExpect(model().attribute("area", "Bình Thạnh"))
        .andExpect(model().attribute("selectedDate", day))
        .andExpect(model().attribute("minPrice", "120000"))
        .andExpect(model().attribute("maxPrice", "120000"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/courts/" + court.getId() + "?date=" + day)))
        .andReturn();
    @SuppressWarnings("unchecked")
    var page = (org.springframework.data.domain.Page<Court>) result.getModelAndView().getModel().get("courts");
    assertThat(page.getContent()).extracting(Court::getId).containsExactly(court.getId());
  }

  @Test
  void searchAreasIncludeActualCourtAreasAndFiltersSurviveBothFormsAndPaging() throws Exception {
    for (int i = 0; i < 9; i++) {
      Court c = newCourt(owner, "Sân ngoại thành " + i, "150000");
      c.setArea("Khu thể thao mới");courts.saveAndFlush(c);
    }
    var result = mvc.perform(get("/").param("sport", "Cầu lông").param("area", "Khu thể thao mới")
        .param("date", day.toString()).param("minPrice", "100000").param("maxPrice", "200000"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("areas", org.hamcrest.Matchers.hasItem("Khu thể thao mới")))
        .andReturn();
    String html = result.getResponse().getContentAsString();
    var heroMatch = java.util.regex.Pattern.compile("(?s)<form class=\"hero-search\".*?</form>").matcher(html);
    assertThat(heroMatch.find()).isTrue();
    String hero = heroMatch.group();
    assertThat(searchInputValue(hero, "minPrice")).isEqualTo("100000");
    assertThat(searchInputValue(hero, "maxPrice")).isEqualTo("200000");
    assertThat(hero).containsPattern("<option(?=[^>]*value=\"Khu thể thao mới\")(?=[^>]*selected=\"selected\")[^>]*>");
    var refineMatch = java.util.regex.Pattern.compile("(?s)<form[^>]*class=\"discovery-filter-form\".*?</form>").matcher(html);
    assertThat(refineMatch.find()).isTrue();
    String refine = refineMatch.group();
    assertThat(searchInputValue(refine, "sport")).isEqualTo("Cầu lông");
    assertThat(searchInputValue(refine, "area")).isEqualTo("Khu thể thao mới");
    assertThat(searchInputValue(refine, "date")).isEqualTo(day.toString());
    assertThat(hero).doesNotContain("name=\"page\"");
    assertThat(refine).doesNotContain("name=\"page\"");
    var nextMatch = java.util.regex.Pattern.compile("(?s)<nav class=\"pager[^>]*>.*?<a[^>]*href=\"([^\"]+)\"").matcher(html);
    assertThat(nextMatch.find()).isTrue();
    String next = org.springframework.web.util.HtmlUtils.htmlUnescape(nextMatch.group(1));
    var nextResult = mvc.perform(get(java.net.URI.create(next.substring(0, next.indexOf('#')))))
        .andExpect(status().isOk()).andExpect(model().attribute("area", "Khu thể thao mới"))
        .andExpect(model().attribute("sport", "Cầu lông"))
        .andExpect(model().attribute("selectedDate", day))
        .andExpect(model().attribute("minPrice", "100000"))
        .andExpect(model().attribute("maxPrice", "200000")).andReturn();
    var nextPage = (org.springframework.data.domain.Page<?>) nextResult.getModelAndView().getModel().get("courts");
    assertThat(nextPage.getNumber()).isEqualTo(1);
    assertThat(nextPage.getContent()).hasSize(1);
  }

  private static String searchInputValue(String form, String name) {
    var input = java.util.regex.Pattern.compile("<input(?=[^>]*name=\"" + name + "\")[^>]*>").matcher(form);
    assertThat(input.find()).isTrue();
    var value = java.util.regex.Pattern.compile("value=\"([^\"]*)\"").matcher(input.group());
    assertThat(value.find()).isTrue();
    return org.springframework.web.util.HtmlUtils.htmlUnescape(value.group(1));
  }

  @Test
  void searchHandlesBlankFiltersNoMatchesAndInvalidRanges() throws Exception {
    mvc.perform(get("/").param("sport", "  ").param("area", "  ").param("minPrice", " "))
        .andExpect(status().isOk()).andExpect(model().attribute("hasSearch", false));
    mvc.perform(get("/").param("sport", "Không có môn này"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa tìm thấy sân phù hợp")));
    for (String price : List.of("-1", "abc")) {
      mvc.perform(get("/").param("minPrice", price)).andExpect(status().isBadRequest());
      mvc.perform(get("/").param("maxPrice", price)).andExpect(status().isBadRequest());
    }
    mvc.perform(get("/").param("minPrice", "200000").param("maxPrice", "100000"))
        .andExpect(status().isBadRequest());
    for (LocalDate invalid : List.of(service.today().minusDays(1), service.today().plusDays(31))) {
      mvc.perform(get("/").param("date", invalid.toString())).andExpect(status().isBadRequest());
    }
    mvc.perform(get("/").param("date", "invalid")).andExpect(status().isBadRequest());
    mvc.perform(get("/").param("date", service.today().plusDays(30).toString()))
        .andExpect(status().isOk());
  }

  @Test
  void authenticatedPagesRender() throws Exception {
    Booking b = service.book(court.getId(), day, 18, guest);
    mvc.perform(get("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk());
    mvc.perform(get("/bookings/" + b.getId()).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk());
    mvc.perform(get("/account").with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk());
    mvc.perform(get("/owner").with(user(owner.getEmail()).roles("OWNER"))).andExpect(status().isOk());
    mvc.perform(get("/owner/courts/new").with(user(owner.getEmail()).roles("OWNER")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/owner/courts/" + court.getId() + "/edit")
                .with(user(owner.getEmail()).roles("OWNER")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/bookings/confirm")
                .with(user(guest.getEmail()).roles("CUSTOMER"))
                .param("courtId", court.getId().toString())
                .param("date", day.toString())
                .param("hour", "19"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Xác nhận đặt sân")));
    mvc.perform(
            get("/bookings/confirm")
                .with(user(guest.getEmail()).roles("CUSTOMER"))
                .param("courtId", court.getId().toString())
                .param("date", day.toString())
                .param("hour", "18"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchClampsPageAndReportsActualResultRangeAndEmptyState() throws Exception {
    for (int i = 0; i < 8; i++) newCourt(owner, "Sân phân trang " + i, "120000");
    var last = mvc.perform(get("/").param("page", "999"))
        .andExpect(status().isOk()).andExpect(model().attribute("resultFrom", 9L))
        .andExpect(model().attribute("resultTo", 9L))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Hiển thị 9–9 trong 9 sân phù hợp")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Chọn ngày để xem lịch trống")))
        .andReturn();
    var page = (org.springframework.data.domain.Page<?>) last.getModelAndView().getModel().get("courts");
    assertThat(page.getNumber()).isEqualTo(1);
    mvc.perform(get("/").param("page", "-1"))
        .andExpect(status().isOk()).andExpect(model().attribute("resultFrom", 1L))
        .andExpect(model().attribute("resultTo", 8L));
    mvc.perform(get("/").param("q", "Không tồn tại"))
        .andExpect(status().isOk()).andExpect(model().attribute("resultFrom", 0L))
        .andExpect(model().attribute("resultTo", 0L))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa tìm thấy sân phù hợp")));
  }

  @Test
  void originalSearchSurvivesDetailDateChangeConfirmationAndReturn() throws Exception {
    for (int i = 0; i < 8; i++) newCourt(owner, "Sân ngữ cảnh " + i, "120000");
    var search = mvc.perform(get("/").param("q", "Sân").param("sport", "Cầu lông")
        .param("area", "Bình Thạnh").param("date", day.toString())
        .param("minPrice", "100000").param("maxPrice", "200000").param("page", "1"))
        .andExpect(status().isOk()).andReturn();
    String returnTo = (String) search.getModelAndView().getModel().get("searchReturnTo");
    String detailUrl = renderedHref(search.getResponse().getContentAsString(),
        "class=\"card-art\" href=\"([^\"]+)\"");
    var detail = mvc.perform(get(java.net.URI.create(detailUrl)).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk()).andExpect(model().attribute("searchReturnTo", returnTo)).andReturn();
    assertThat(searchInputValue(detail.getResponse().getContentAsString(), "returnTo")).isEqualTo(returnTo);
    var selected = (Court) detail.getModelAndView().getModel().get("court");
    var changed = mvc.perform(get("/courts/" + selected.getId()).param("date", day.plusDays(1).toString())
        .param("returnTo", returnTo).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk()).andReturn();
    String confirmUrl = renderedHref(changed.getResponse().getContentAsString(),
        "href=\"([^\"]*/bookings/confirm[^\"]*hour=18[^\"]*)\"");
    var confirm = mvc.perform(get(java.net.URI.create(confirmUrl)).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk()).andExpect(model().attribute("date", day.plusDays(1)))
        .andExpect(model().attribute("searchReturnTo", returnTo)).andReturn();
    assertThat(searchInputValue(confirm.getResponse().getContentAsString(), "returnTo")).isEqualTo(returnTo);
    String back = renderedHref(detail.getResponse().getContentAsString(), "href=\"([^\"]+)\" class=\"back-link\"");
    var restored = mvc.perform(get(java.net.URI.create(back.substring(0, back.indexOf('#')))))
        .andExpect(status().isOk()).andExpect(model().attribute("q", "Sân"))
        .andExpect(model().attribute("sport", "Cầu lông"))
        .andExpect(model().attribute("area", "Bình Thạnh"))
        .andExpect(model().attribute("selectedDate", day))
        .andExpect(model().attribute("minPrice", "100000"))
        .andExpect(model().attribute("maxPrice", "200000")).andReturn();
    assertThat(((org.springframework.data.domain.Page<?>) restored.getModelAndView().getModel().get("courts")).getNumber()).isEqualTo(1);
  }

  @Test
  void returnLinksRejectExternalAndUnrelatedDestinations() throws Exception {
    for (String invalid : List.of("https://example.com", "//example.com", "/owner", "/?q=x\r\n")) {
      mvc.perform(get("/courts/" + court.getId()).param("returnTo", invalid))
          .andExpect(status().isOk()).andExpect(model().attribute("searchReturnTo", "/"));
      mvc.perform(get("/bookings/confirm").param("courtId", court.getId().toString())
          .param("date", day.toString()).param("hour", "18").param("returnTo", invalid)
          .with(user(guest.getEmail()).roles("CUSTOMER")))
          .andExpect(status().isOk()).andExpect(model().attribute("searchReturnTo", "/"));
    }
  }

  @Test
  void stalePriceReturnsToDetailWithOriginalSearchContext() throws Exception {
    String context = CustomerNavigation.searchUrl("Sân", "Cầu lông", "Bình Thạnh", day, "100000", "200000", 0);
    var rejected = mvc.perform(post("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf())
        .param("courtId", court.getId().toString()).param("date", day.toString())
        .param("hour", "18").param("expectedPrice", "1").param("returnTo", context))
        .andExpect(status().is3xxRedirection())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("error"))
        .andReturn();
    assertThat(bookings.count()).isZero();
    mvc.perform(get(java.net.URI.create(rejected.getResponse().getRedirectedUrl())))
        .andExpect(status().isOk()).andExpect(model().attribute("date", day))
        .andExpect(model().attribute("searchReturnTo", context));
  }

  private static String renderedHref(String html, String expression) {
    var link = java.util.regex.Pattern.compile(expression).matcher(html);
    assertThat(link.find()).isTrue();
    return org.springframework.web.util.HtmlUtils.htmlUnescape(link.group(1));
  }

  @Test
  void customerCanFollowDetailConfirmAndCreateBookingWithQuotedPeakPrice() throws Exception {
    PriceRule peak = new PriceRule();
    peak.setCourt(court);peak.setStartHour(17);peak.setEndHour(22);
    peak.setPrice(new BigDecimal("180000"));prices.saveAndFlush(peak);
    var detail = mvc.perform(get("/courts/" + court.getId()).param("date", day.toString())
        .with(user(guest.getEmail()).roles("CUSTOMER"))).andExpect(status().isOk()).andReturn();
    var link = java.util.regex.Pattern.compile("href=\"([^\"]*/bookings/confirm[^\"]*hour=18[^\"]*)\"")
        .matcher(detail.getResponse().getContentAsString());
    assertThat(link.find()).isTrue();
    String url = org.springframework.web.util.HtmlUtils.htmlUnescape(link.group(1));
    var confirmation = mvc.perform(get(java.net.URI.create(url)).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk()).andExpect(model().attribute("date", day))
        .andExpect(model().attribute("hour", 18))
        .andExpect(model().attribute("price", org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("180000"))))
        .andExpect(model().attribute("deposit", org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("90000"))))
        .andReturn();
    String form = confirmation.getResponse().getContentAsString();
    mvc.perform(post("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf())
        .param("courtId", searchInputValue(form, "courtId"))
        .param("date", searchInputValue(form, "date"))
        .param("hour", searchInputValue(form, "hour"))
        .param("expectedPrice", searchInputValue(form, "expectedPrice")))
        .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/bookings/*"));
    Booking booking = bookings.findAll().get(0);
    assertThat(booking.getPlayDate()).isEqualTo(day);
    assertThat(booking.getStartHour()).isEqualTo(18);
    assertThat(booking.getAmount()).isEqualByComparingTo("180000");
    assertThat(booking.getDepositAmount()).isEqualByComparingTo("90000");
    assertThat(booking.getStatus()).isEqualTo("PENDING_DEPOSIT");
    mvc.perform(get("/bookings/" + booking.getId()).with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk());
    assertThat(service.slots(court, day)).filteredOn(slot -> slot.hour() == 18)
        .extracting(BookingService.Slot::state).containsExactly("BOOKED");
  }

  @Test
  void bookingRejectsPriceChangedAfterConfirmationWithoutCreatingAnOrder() throws Exception {
    mvc.perform(get("/bookings/confirm").param("courtId", court.getId().toString())
        .param("date", day.toString()).param("hour", "18")
        .with(user(guest.getEmail()).roles("CUSTOMER"))).andExpect(status().isOk());
    court.setPrice(new BigDecimal("200000"));court.setMinPrice(new BigDecimal("200000"));
    courts.saveAndFlush(court);
    mvc.perform(post("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf())
        .param("courtId", court.getId().toString()).param("date", day.toString())
        .param("hour", "18").param("expectedPrice", "120000"))
        .andExpect(status().is3xxRedirection())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/courts/" + court.getId() + "?date=" + day))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("error"));
    assertThat(bookings.count()).isZero();
    mvc.perform(get("/bookings/confirm").param("courtId", court.getId().toString())
        .param("date", day.toString()).param("hour", "18")
        .with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("price", org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("200000"))));
  }

  @Test
  void staleConfirmationCannotBookNewlyBlockedOrTakenSlot() throws Exception {
    mvc.perform(get("/bookings/confirm").param("courtId", court.getId().toString())
        .param("date", day.toString()).param("hour", "18")
        .with(user(guest.getEmail()).roles("CUSTOMER"))).andExpect(status().isOk());
    service.toggleBlock(court.getId(), day, 18, owner);
    mvc.perform(post("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf())
        .param("courtId", court.getId().toString()).param("date", day.toString())
        .param("hour", "18").param("expectedPrice", "120000"))
        .andExpect(status().is3xxRedirection())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("error"));
    assertThat(bookings.count()).isZero();
    service.toggleBlock(court.getId(), day, 18, owner);
    service.book(court.getId(), day, 18, other);
    mvc.perform(post("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf())
        .param("courtId", court.getId().toString()).param("date", day.toString())
        .param("hour", "18").param("expectedPrice", "120000"))
        .andExpect(status().is3xxRedirection())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("error"));
    assertThat(bookings.count()).isEqualTo(1);
  }

  @Test
  void confirmationRequiresCustomerAndExplainsMissingPhoneBeforeSubmit() throws Exception {
    mvc.perform(get("/bookings/confirm").param("courtId", court.getId().toString())
        .param("date", day.toString()).param("hour", "18").accept(org.springframework.http.MediaType.TEXT_HTML))
        .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    mvc.perform(get("/bookings/confirm").param("courtId", court.getId().toString())
        .param("date", day.toString()).param("hour", "18")
        .with(user(owner.getEmail()).roles("OWNER"))).andExpect(status().isForbidden());
    guest.setPhone(null);accounts.saveAndFlush(guest);
    mvc.perform(get("/bookings/confirm").param("courtId", court.getId().toString())
        .param("date", day.toString()).param("hour", "18")
        .with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isOk()).andExpect(model().attribute("missingPhone", true))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("cập nhật số điện thoại")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("disabled=\"disabled\"")));
    mvc.perform(post("/bookings").with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf())
        .param("courtId", court.getId().toString()).param("date", day.toString())
        .param("hour", "18").param("expectedPrice", "120000"))
        .andExpect(status().is3xxRedirection())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("error"));
    assertThat(bookings.count()).isZero();
  }

  @Test
  void securityAndCsrfAreEnforced() throws Exception {
    mvc.perform(get("/owner").with(user(guest.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/bookings")
                .with(user(guest.getEmail()).roles("CUSTOMER"))
                .param("courtId", court.getId().toString())
                .param("date", day.toString())
                .param("hour", "18"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/bookings")
                .with(user(guest.getEmail()).roles("CUSTOMER"))
                .with(csrf())
                .param("courtId", court.getId().toString())
                .param("date", day.toString())
                .param("hour", "18"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrlPattern("/bookings/*"));
    Booking b = bookings.findAll().get(0);
    mvc.perform(get("/bookings/" + b.getId()).with(user(other.getEmail()).roles("CUSTOMER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void registrationCannotChooseOwnerRole() throws Exception {
    mvc.perform(
            post("/register")
                .with(csrf())
                .param("name", "Bạn mới")
                .param("email", "new@test.vn")
                .param("phone", "0901234567")
                .param("password", "Password123")
                .param("role", "OWNER"))
        .andExpect(status().is3xxRedirection());
    Account a = accounts.findByEmail("new@test.vn").orElseThrow();
    assertThat(a.getRole()).isEqualTo("CUSTOMER");
    assertThat(a.getPassword()).startsWith("$2");
  }

  @Test
  void completedRevenueExcludesConfirmedAndCancelled() throws Exception {
    Booking completed = service.book(court.getId(), day, 16, guest);
    completed.setPlayDate(service.today().minusDays(1));
    completed.setStatus("CONFIRMED");
    bookings.saveAndFlush(completed);
    service.complete(completed.getId(), owner);
    service.book(court.getId(), day, 18, guest);
    Booking cancelled = service.book(court.getId(), day, 19, guest);
    service.cancel(cancelled.getId(), guest);
    mvc.perform(
            get("/owner")
                .with(user(owner.getEmail()).roles("OWNER"))
                .param("from", service.today().minusDays(5).toString())
                .param("to", service.today().toString()))
        .andExpect(status().isOk())
        .andExpect(
            model()
                .attribute(
                    "revenue", org.hamcrest.Matchers.comparesEqualTo(new BigDecimal("120000"))))
        .andExpect(model().attribute("paidCount", 1));
  }

  @Test
  void secondOwnerCannotEditFirstCourt() throws Exception {
    mvc.perform(
            get("/owner/courts/" + court.getId() + "/edit")
                .with(user(secondOwner.getEmail()).roles("OWNER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsNonImageUpload() throws Exception {
    MockMultipartFile exe =
        new MockMultipartFile("photos", "malware.exe", "application/octet-stream", new byte[] {0x4D, 0x5A, 0x00});
    mvc.perform(
            multipart("/owner/courts/save")
                .file(exe)
                .with(user(owner.getEmail()).roles("OWNER"))
                .with(csrf())
                .param("id", court.getId().toString())
                .param("name", court.getName())
                .param("sport", court.getSport())
                .param("area", court.getArea())
                .param("address", court.getAddress())
                .param("description", "mô tả")
                .param("amenities", "đèn")
                .param("price", "120000")
                .param("openHour", "6")
                .param("closeHour", "22")
                .param("active", "true"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("JPEG")));
    assertThat(images.count()).isZero();
  }
}
