package vn.uth.court;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** /health và lỗi nghiệp vụ dùng chung: không phụ thuộc Gemini, không trả 500 cho dữ liệu sai. */
@SpringBootTest(properties = {"app.demo.seed=false", "GEMINI_API_KEY=", "GEMINI_MODEL="})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class WebErrorsTest {
  @Autowired MockMvc mvc;

  @Test
  void healthChecksDatabaseWithoutAiConfiguration() throws Exception {
    mvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void missingCourtRendersVietnameseNotFoundPage() throws Exception {
    mvc.perform(get("/courts/999999"))
        .andExpect(status().isNotFound())
        .andExpect(view().name("error"))
        .andExpect(content().string(containsString("Không tìm thấy sân.")));
  }

  @Test
  void invalidBusinessInputIsBadRequestNotServerError() throws Exception {
    mvc.perform(get("/").param("minPrice", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(view().name("error"))
        .andExpect(content().string(containsString("Giá không được nhỏ hơn 0.")));
  }
}
