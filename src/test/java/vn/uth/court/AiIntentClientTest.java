package vn.uth.court;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class AiIntentClientTest {
  final ObjectMapper json=new ObjectMapper();
  HttpServer server;
  URI endpoint;
  @BeforeEach void start() throws Exception {
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1beta/models/gemini-3.5-flash-lite:generateContent");
  }
  @AfterEach void stop() { server.stop(0); }
  @Test void realHttpRequestUsesGeminiSchemaAndHeader() throws Exception {
    AtomicReference<JsonNode> request=new AtomicReference<>();
    server.createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent",exchange->{
      assertThat(exchange.getRequestHeaders().getFirst("x-goog-api-key")).isEqualTo("test-key");
      assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isNull();
      assertThat(exchange.getRequestURI().getQuery()).isNull();
      request.set(json.readTree(exchange.getRequestBody()));
      var intent=Map.of("action","search","sport","Cầu lông","area","Thủ Đức","date","2026-11-03","hour",19,"minPrice",0,"maxPrice",200000);
      byte[] response=json.writeValueAsBytes(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("role","model","parts",List.of(Map.of("text",json.writeValueAsString(intent))))))));
      exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
    });server.start();
    var client=new AiIntentClient(json,"test-key","gemini-3.5-flash-lite",endpoint);
    var result=client.interpret(List.of(new AiIntentClient.Turn("assistant","Bạn muốn tìm sân ở đâu?")),"Thủ Đức ngày mai 19h","2026-11-02");
    assertThat(result.path("hour").asInt()).isEqualTo(19);
    assertThat(request.get().at("/generationConfig/responseMimeType").asText()).isEqualTo("application/json");
    assertThat(request.get().at("/generationConfig/responseJsonSchema/type").asText()).isEqualTo("object");
    assertThat(request.get().path("contents").size()).isEqualTo(2);
    assertThat(request.get().at("/contents/0/role").asText()).isEqualTo("model");
    assertThat(request.get().at("/contents/1/role").asText()).isEqualTo("user");
    assertThat(request.get().at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(2048);
    assertThat(request.get().has("model")).isFalse();
    assertThat(request.get().has("input")).isFalse();
    assertThat(request.get().has("store")).isFalse();
    assertThat(request.get().toString()).doesNotContain("test-key");
  }
  @Test void upstreamFailureNeverLeaksResponseBody() {
    server.createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent",exchange->{byte[] b="secret-provider-error".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(429,b.length);exchange.getResponseBody().write(b);exchange.close();});server.start();
    assertThatThrownBy(()->new AiIntentClient(json,"test-key","gemini-3.5-flash-lite",endpoint).interpret(List.of(),"hello","2026-11-02"))
        .isInstanceOf(AiUnavailableException.class).hasMessageNotContaining("secret-provider-error").hasMessageNotContaining("test-key");
  }
  @Test void malformedOrRefusedOutputFailsClosed() {
    server.createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent",exchange->{byte[] b="{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,b.length);exchange.getResponseBody().write(b);exchange.close();});server.start();
    assertThatThrownBy(()->new AiIntentClient(json,"test-key","gemini-3.5-flash-lite",endpoint).interpret(List.of(),"hello","2026-11-02")).isInstanceOf(AiUnavailableException.class);
  }
  @Test void chatEndpointSendsGeminiRequestAndReturnsDatabaseServiceAnswer() throws Exception {
    var calls=new java.util.concurrent.atomic.AtomicInteger();
    server.createContext("/v1beta/models/gemini-3.5-flash-lite:generateContent",exchange->{
      calls.incrementAndGet();
      var body=json.readTree(exchange.getRequestBody());
      assertThat(body.at("/contents/0/parts/0/text").asText()).isEqualTo("Tìm sân cầu lông");
      assertThat(exchange.getRequestHeaders().getFirst("x-goog-api-key")).isEqualTo("test-key");
      byte[] response=json.writeValueAsBytes(Map.of("candidates",List.of(Map.of("finishReason","STOP","content",Map.of("parts",List.of(Map.of("text","{\"action\":\"search\"}")))))));
      exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
    });server.start();
    var data=org.mockito.Mockito.mock(CourtAssistantService.class);
    org.mockito.Mockito.when(data.today()).thenReturn("2026-11-02");
    org.mockito.Mockito.when(data.answer(org.mockito.ArgumentMatchers.any())).thenReturn(new CourtAssistantService.Reply("Bạn muốn chơi khu vực nào?",List.of()));
    var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
        new AssistantController(new AiIntentClient(json,"test-key","gemini-3.5-flash-lite",endpoint),data)).build();
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/assistant/messages")
        .contentType("application/json").content(json.writeValueAsString(Map.of("message","Tìm sân cầu lông"))))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("Bạn muốn chơi khu vực nào?"));
    assertThat(calls.get()).isEqualTo(1);
    org.mockito.Mockito.verify(data).answer(json.readTree("{\"action\":\"search\"}"));
  }
  @Test void productionEndpointOnlyTargetsGemini() {
    assertThat(AiIntentClient.geminiEndpoint("gemini-3.5-flash-lite").toString())
        .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent");
    assertThatThrownBy(()->AiIntentClient.geminiEndpoint("../openai")).isInstanceOf(IllegalArgumentException.class);
    assertThat(new AiIntentClient(json,"","gemini-3.5-flash-lite").available()).isFalse();
  }
  @Test void missingKeyDoesNotCallNetwork() {
    var client=new AiIntentClient(json,"","gemini-3.5-flash-lite",endpoint);
    assertThat(client.available()).isFalse();
    assertThatThrownBy(()->client.interpret(List.of(),"hello","2026-11-02")).isInstanceOf(AiUnavailableException.class);
  }
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.NullAndEmptySource
  @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "../model", "models/gemini-3.5-flash-lite",
      "\"gemini-3.5-flash-lite\"", "GEMINI_MODEL=gemini-3.5-flash-lite", "https://example.org/model"})
  void invalidModelDisablesAiInsteadOfThrowingInConstructor(String model) {
    var client = new AiIntentClient(json, "config-test-key", model);
    assertThat(client.available()).isFalse();
    assertThatThrownBy(() -> client.interpret(List.of(), "hello", "2026-11-02"))
        .isInstanceOf(AiUnavailableException.class).hasMessageNotContaining("config-test-key");
  }
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.NullAndEmptySource
  @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "bad\nheader", "bad key", "bad\u007fkey"})
  void missingOrMalformedKeyDisablesAiWithoutCreatingANetworkClient(String key) {
    var client = new AiIntentClient(json, key, "gemini-3.5-flash-lite");
    assertThat(client.available()).isFalse();
    assertThatThrownBy(() -> client.interpret(List.of(), "hello", "2026-11-02"))
        .isInstanceOf(AiUnavailableException.class);
  }
  @Test void surroundingWhitespaceInEnvironmentValuesIsTrimmed() {
    assertThat(new AiIntentClient(json, " config-test-key ", " gemini-3.5-flash-lite ").available()).isTrue();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({"429,QUOTA", "403,CONFIGURATION", "404,CONFIGURATION", "503,BUSY", "504,TIMEOUT"})
  void reportsProviderFailureWithoutLeakingSecrets(int status, AiUnavailableException.Reason reason) {
    server.createContext(endpoint.getPath(),exchange->{
      byte[] body="upstream-private-test-key".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();
    });server.start();
    assertThatThrownBy(()->new AiIntentClient(json,"test-key","gemini-test",endpoint).interpret(List.of(),"hello","2026-11-02"))
        .isInstanceOfSatisfying(AiUnavailableException.class,ex->{
          assertThat(ex.reason()).isEqualTo(reason);
          assertThat(ex.getMessage()).doesNotContain("test-key","upstream-private");
        });
  }
  @Test void truncatedOutputIsNotMisreportedAsConnectionFailure() {
    server.createContext(endpoint.getPath(),exchange->{
      byte[] body="{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
    });server.start();
    assertThatThrownBy(()->new AiIntentClient(json,"test-key","gemini-test",endpoint).interpret(List.of(),"hello","2026-11-02"))
        .isInstanceOfSatisfying(AiUnavailableException.class,ex->assertThat(ex.reason()).isEqualTo(AiUnavailableException.Reason.INCOMPLETE));
  }

}
