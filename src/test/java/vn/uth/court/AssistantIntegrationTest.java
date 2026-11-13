package vn.uth.court;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"app.demo.seed=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc @Transactional
class AssistantIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired CourtAssistantService assistant;
  @Autowired AccountRepository accounts;
  @Autowired CourtRepository courts;
  @Autowired BookingService booking;
  @Autowired PriceRuleRepository prices;
  @MockitoBean AiIntentClient ai;
  JsonNode intent;
  Court court;
  @BeforeEach void setup() throws Exception {
    when(ai.available()).thenReturn(true);
    intent=json.readTree("{\"action\":\"search\",\"sport\":\"Cầu lông\",\"area\":\"Thủ Đức\",\"date\":\""+booking.today().plusDays(1)+"\",\"hour\":19,\"minPrice\":null,\"maxPrice\":null}");
    when(ai.interpret(anyList(),anyString(),anyString())).thenReturn(intent);
    Account owner=new Account();owner.setName("Private Owner");owner.setEmail("assistant-owner@test.vn");owner.setPassword("unused");owner.setRole("OWNER");accounts.saveAndFlush(owner);
    court=new Court();court.setName("Sân dữ liệu thật");court.setSport("Cầu lông");court.setArea("Thủ Đức");court.setAddress("Địa chỉ công khai");court.setPrice(new BigDecimal("120000"));court.setMinPrice(new BigDecimal("120000"));court.setOpenHour(6);court.setCloseHour(23);court.setActive(true);court.setOwner(owner);courts.saveAndFlush(court);
  }
  @Test void usesActualSlotsPricesAndLocalLinks() {
    var reply=assistant.answer(intent);
    assertThat(reply.message()).contains("Sân dữ liệu thật","120000 VND/giờ").doesNotContain("assistant-owner","Private Owner");
    assertThat(reply.links()).hasSize(1);
    assertThat(reply.links().getFirst().url()).isEqualTo("/courts/"+court.getId()+"?date="+booking.today().plusDays(1));
    booking.toggleBlock(court.getId(),booking.today().plusDays(1),19,court.getOwner());
    assertThat(assistant.answer(intent).links()).isEmpty();
  }
  @Test void actualHourPriceFiltersAndInactiveCourts() {
    ((com.fasterxml.jackson.databind.node.ObjectNode)intent).put("maxPrice",100000);
    assertThat(assistant.answer(intent).links()).isEmpty();
    ((com.fasterxml.jackson.databind.node.ObjectNode)intent).putNull("maxPrice");
    court.setActive(false);courts.saveAndFlush(court);
    assertThat(assistant.answer(intent).links()).isEmpty();
  }
  @Test void priceRulesOverrideAdvertisedMinimumPrice() {
    PriceRule rule=new PriceRule();rule.setCourt(court);rule.setStartHour(18);rule.setEndHour(21);rule.setPrice(new BigDecimal("250000"));prices.saveAndFlush(rule);
    assertThat(assistant.answer(intent).message()).contains("250000 VND/giờ");
    ((com.fasterxml.jackson.databind.node.ObjectNode)intent).put("maxPrice",200000);
    assertThat(assistant.answer(intent).links()).isEmpty();
  }
  @Test void asksMissingDetailsAndRejectsOutOfRangeDate() throws Exception {
    var missing=json.readTree("{\"action\":\"search\",\"sport\":\"Cầu lông\"}");
    assertThat(assistant.answer(missing).message()).contains("khu vực","ngày chơi","giờ bắt đầu");
    ((com.fasterxml.jackson.databind.node.ObjectNode)intent).put("date",booking.today().minusDays(1).toString());
    assertThat(assistant.answer(intent).links()).isEmpty();
    assertThat(assistant.answer(intent).message()).contains("30 ngày");
  }
  @Test void cancellationOnlyProvidesInstructions() throws Exception {
    var reply=assistant.answer(json.readTree("{\"action\":\"cancel_help\"}"));
    assertThat(reply.message()).contains("trên 2 tiếng","tiền cọc bị giữ lại","không tự hủy");
    assertThat(reply.links().getFirst().url()).isEqualTo("/bookings");
  }
  @Test void publicApiRequiresCsrfAndValidLength() throws Exception {
    mvc.perform(post("/api/assistant/messages").contentType("application/json").content("{\"message\":\"Tìm sân\"}")).andExpect(status().isForbidden());
    mvc.perform(post("/api/assistant/messages").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("message","x".repeat(1001))))).andExpect(status().isBadRequest());
    mvc.perform(post("/api/assistant/messages").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("message","x".repeat(9000))))).andExpect(status().isPayloadTooLarge());
  }
  @Test void missingKeyAndProviderFailureHaveSafeMessages() throws Exception {
    when(ai.available()).thenReturn(false);
    mvc.perform(post("/api/assistant/messages").with(csrf()).contentType("application/json").content("{\"message\":\"Tìm sân\"}")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("chưa cấu hình")));
    when(ai.available()).thenReturn(true);when(ai.interpret(anyList(),anyString(),anyString())).thenThrow(new AiUnavailableException());
    mvc.perform(post("/api/assistant/messages").with(csrf()).contentType("application/json").content("{\"message\":\"Tìm sân\"}")).andExpect(status().isServiceUnavailable());
  }
  @Test void boundedServerHistoryAndRateLimitSurviveReset() throws Exception {
    var session=new MockHttpSession();
    for(int i=0;i<8;i++) mvc.perform(post("/api/assistant/messages").session(session).with(csrf()).contentType("application/json").content("{\"message\":\"Tìm sân\"}")).andExpect(status().isOk());
    var captor=org.mockito.ArgumentCaptor.forClass(List.class);verify(ai,times(8)).interpret(captor.capture(),anyString(),anyString());
    assertThat(captor.getAllValues()).allSatisfy(value->assertThat(value.size()).isLessThanOrEqualTo(8));
    mvc.perform(delete("/api/assistant/history").session(session).with(csrf())).andExpect(status().isNoContent());
    mvc.perform(post("/api/assistant/messages").session(session).with(csrf()).contentType("application/json").content("{\"message\":\"Tìm sân\"}")).andExpect(status().isTooManyRequests());
  }
}
