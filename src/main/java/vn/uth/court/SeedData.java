package vn.uth.court;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@org.springframework.context.annotation.Profile("demo & !mysql & !prod & !shared & !render & !hosting")
public class SeedData {
  @Bean
  @ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
  CommandLineRunner seed(
      AccountRepository accounts,
      CourtRepository courts,
      BookingRepository bookings,
      PasswordEncoder encoder,
      Clock clock) {
    return args -> {
      if (accounts.count() > 0) {
        return;
      }
      Account owner = account(accounts, encoder, "Chủ sân Demo", "owner@demo.vn", "0900000001", "OWNER");
      Account customer = account(accounts, encoder, "Khách Demo", "guest@demo.vn", "0900000002", "CUSTOMER");
      String[][] samples = {
        {"Cầu lông Bình Minh", "Cầu lông", "Bình Thạnh", "Bình Thạnh, TP. Hồ Chí Minh", "120000", "180000"},
        {"Sân bóng Thành Phố", "Bóng đá", "Thủ Đức", "Thủ Đức, TP. Hồ Chí Minh", "350000", "450000"},
        {"Tennis Riverside", "Tennis", "Quận 7", "Quận 7, TP. Hồ Chí Minh", "220000", "280000"},
        {"Bóng rổ Central", "Bóng rổ", "Gò Vấp", "Gò Vấp, TP. Hồ Chí Minh", "180000", "220000"}
      };
      Court first = null;
      for (String[] s : samples) {
        Court c = new Court();
        c.setName(s[0]);
        c.setSport(s[1]);
        c.setArea(s[2]);
        c.setAddress(s[3]);
        c.setPrice(new BigDecimal(s[4]));
        c.setMinPrice(new BigDecimal(s[4]));
        c.setDescription(
            "Sân mẫu phục vụ demo đề án. Không phải cơ sở kinh doanh thực tế. Có khu vực nghỉ và chỗ để xe.");
        c.setAmenities("Đèn chiếu sáng, nhà vệ sinh, chỗ để xe, nước uống");
        c.setOpenHour(6);
        c.setCloseHour(22);
        c.setActive(true);
        c.setOwner(owner);
        PriceRule peak = new PriceRule();
        peak.setCourt(c);
        peak.setStartHour(17);
        peak.setEndHour(22);
        peak.setPrice(new BigDecimal(s[5]));
        c.getPriceRules().add(peak);
        courts.save(c);
        if (first == null) {
          first = c;
        }
      }
      Booking past = new Booking();
      past.setCourt(first);
      past.setCustomer(customer);
      past.setPlayDate(LocalDate.now(clock).minusDays(1));
      past.setStartHour(18);
      past.setAmount(new BigDecimal("180000"));
      past.setStatus("COMPLETED");
      past.setActiveSlotKey(BookingService.key(first.getId(), past.getPlayDate(), 18));
      past.setCreatedAt(LocalDateTime.now(clock).minusDays(2));
      bookings.save(past);
    };
  }

  private static Account account(
      AccountRepository repo, PasswordEncoder encoder, String name, String email, String phone, String role) {
    Account a = new Account();
    a.setName(name);
    a.setEmail(email);
    a.setPhone(phone);
    a.setPassword(encoder.encode("Demo@12345"));
    a.setRole(role);
    return repo.save(a);
  }
}
