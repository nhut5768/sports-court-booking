package vn.uth.court;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Boots the real Render profile with an invalid environment-style Gemini model and no provider calls. */
@SpringBootTest(properties = {
    "app.demo.seed=false",
    "DB_URL=jdbc:h2:mem:render-ai-resilience;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "DB_USERNAME=sa", "DB_PASSWORD=",
    "GEMINI_API_KEY=config-test-key", "GEMINI_MODEL=models/invalid-model",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
@ActiveProfiles({"demo", "render"})
@AutoConfigureMockMvc
@Transactional
class AiConfigurationResilienceTest {
  @Autowired MockMvc mvc;
  @Autowired AiIntentClient ai;
  @Autowired AccountRepository accounts;
  @Autowired CourtRepository courts;
  @Autowired PasswordEncoder encoder;
  @Autowired CourtMessagingService chat;

  Account account(String role) {
    var a = new Account();
    a.setEmail(UUID.randomUUID() + "@test.vn"); a.setName("Test " + role); a.setRole(role);
    a.setPassword(encoder.encode("TestPassword123!"));
    return accounts.saveAndFlush(a);
  }
  @Test void websiteAndDatabaseStayHealthyWhileOnlyAiIsUnavailable() throws Exception {
    assertThat(ai.available()).isFalse();
    mvc.perform(get("/")).andExpect(status().isOk());
    mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/api/assistant/status")).andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false));
    mvc.perform(post("/api/assistant/messages").with(csrf())
        .contentType("application/json").content("{\"message\":\"Tìm sân cầu lông\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("config-test-key"))));
  }
  @Test void passwordLoginAndCsrfRemainActive() throws Exception {
    var customer = account("CUSTOMER");
    mvc.perform(post("/login").with(csrf()).param("username",customer.getEmail())
        .param("password","TestPassword123!")).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));
    mvc.perform(post("/api/assistant/messages")
        .contentType("application/json").content("{\"message\":\"hello\"}"))
        .andExpect(status().isForbidden());
  }
  @Test void customerAndOwnerCanStillExchangeMessages() {
    var owner=account("OWNER"); var customer=account("CUSTOMER");
    var court=new Court(); court.setOwner(owner); court.setName("Sân kiểm thử");
    court.setSport("Cầu lông"); court.setArea("Thủ Đức"); court.setAddress("Địa chỉ kiểm thử");
    court.setPrice(new BigDecimal("100000")); court.setMinPrice(new BigDecimal("100000"));
    court.setOpenHour(6); court.setCloseHour(23); court.setActive(true); courts.saveAndFlush(court);
    long id=chat.openOwner(owner.getId(),customer.getEmail());
    chat.send(id,customer.getEmail(),UUID.randomUUID().toString(),"Tôi muốn hỏi chủ sân.",false);
    chat.send(id,owner.getEmail(),UUID.randomUUID().toString(),"Chủ sân đang trả lời bạn.",false);
    assertThat(chat.history(id,customer.getEmail(),0,0).messages())
        .extracting(CourtMessagingService.MessageView::content)
        .contains("Tôi muốn hỏi chủ sân.","Chủ sân đang trả lời bạn.");
  }
}
