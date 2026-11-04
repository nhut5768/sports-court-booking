package vn.uth.court;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"app.demo.seed=false", "app.upload-dir=${java.io.tmpdir}/san-nha-mysql-tests"})
@ActiveProfiles("mysql")
@Testcontainers(disabledWithoutDocker = true)
class MysqlBookingIT {
  @Container
  static final MySQLContainer<?> MYSQL =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("sports_court_booking")
          .withUsername("court_app")
          .withPassword("test-pass");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired AccountRepository accounts;
  @Autowired CourtRepository courts;
  @Autowired BookingRepository bookings;
  @Autowired BlockedSlotRepository blocks;
  @Autowired PriceRuleRepository prices;
  @Autowired CourtImageRepository images;
  @Autowired BookingService service;

  @BeforeEach
  void setup() {
    images.deleteAll();
    prices.deleteAll();
    blocks.deleteAll();
    bookings.deleteAll();
    courts.deleteAll();
    accounts.deleteAll();
  }

  @Test
  void flywaySchemaAllowsConcurrentBookingOnMysql() throws Exception {
    Account owner = account("owner-mysql@test.vn", "0900000001", "OWNER");
    Account guest = account("guest-mysql@test.vn", "0900000002", "CUSTOMER");
    Court court = new Court();
    court.setName("Sân MySQL");
    court.setSport("Tennis");
    court.setArea("Quận 7");
    court.setAddress("Quận 7");
    court.setPrice(new BigDecimal("200000"));
    court.setMinPrice(new BigDecimal("200000"));
    court.setOpenHour(7);
    court.setCloseHour(21);
    court.setActive(true);
    court.setOwner(owner);
    courts.saveAndFlush(court);
    LocalDate day = service.today().plusDays(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch gate = new CountDownLatch(1);
    Callable<Boolean> action =
        () -> {
          gate.await();
          try {
            service.book(court.getId(), day, 10, guest);
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
      assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(bookings.count()).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  private Account account(String email, String phone, String role) {
    Account a = new Account();
    a.setName(email);
    a.setEmail(email);
    a.setPhone(phone);
    a.setPassword("unused");
    a.setRole(role);
    return accounts.saveAndFlush(a);
  }
}
