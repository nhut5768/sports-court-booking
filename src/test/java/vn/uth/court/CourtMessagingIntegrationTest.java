package vn.uth.court;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.demo.seed=false"})
@ActiveProfiles("demo") @AutoConfigureMockMvc
class CourtMessagingIntegrationTest {
  @LocalServerPort int port;
  @Autowired CourtMessagingService chat;
  @Autowired CourtConversationRepository conversations;
  @Autowired CourtChatMessageRepository messages;
  @Autowired AccountRepository accounts;
  @Autowired CourtRepository courts;
  @Autowired PasswordEncoder encoder;
  @Autowired PlatformTransactionManager transactions;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UnifiedChatAi jobs;
  @Autowired CourtAssistantService assistant;
  @org.springframework.test.context.bean.override.mockito.MockitoBean AiIntentClient ai;
  Account owner,guest,otherOwner,otherGuest;
  Court court,secondCourt;
  final String password="ChatPassword123";
  @BeforeEach void setup(){
    org.mockito.Mockito.when(ai.available()).thenReturn(false);
    owner=account("owner");owner.setRole("OWNER");accounts.saveAndFlush(owner);
    guest=account("guest");otherOwner=account("other-owner");otherOwner.setRole("OWNER");accounts.saveAndFlush(otherOwner);otherGuest=account("other-guest");
    court=court(owner);secondCourt=court(otherOwner);
  }
  Account account(String prefix){
    var a=new Account();a.setName(prefix);a.setEmail(prefix+UUID.randomUUID()+"@test.vn");a.setRole("CUSTOMER");a.setPassword(encoder.encode(password));return accounts.saveAndFlush(a);
  }
  Court court(Account owner){
    var c=new Court();c.setName("Sân chat "+owner.getName());c.setOwner(owner);c.setSport("Cầu lông");c.setArea("Thủ Đức");c.setAddress("Địa chỉ sân");c.setPrice(new BigDecimal("120000"));c.setMinPrice(c.getPrice());c.setOpenHour(6);c.setCloseHour(23);c.setActive(true);return courts.saveAndFlush(c);
  }
  @AfterEach void cleanup(){
    org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(jobs::idle);
    messages.deleteAll();conversations.deleteAll();courts.delete(secondCourt);courts.delete(court);
    for(var a:List.of(owner,guest,otherOwner,otherGuest))accounts.delete(a);
  }
  String uuid(){return UUID.randomUUID().toString();}
  @Test void opensCorrectOwnerAndKeepsCourtContext(){
    long id=chat.open(court.getId(),guest.getEmail());
    assertThat(chat.open(court.getId(),guest.getEmail())).isEqualTo(id);
    assertThat(chat.detail(id,guest.getEmail()).peerName()).isEqualTo(owner.getName());
    assertThat(chat.detail(id,owner.getEmail()).peerName()).isEqualTo(guest.getName());
    assertThat(chat.detail(id,owner.getEmail()).courtId()).isEqualTo(court.getId());
    assertThat(chat.open(secondCourt.getId(),guest.getEmail())).isNotEqualTo(id);
    assertThatThrownBy(()->chat.open(court.getId(),owner.getEmail())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
  @Test void protectsApiHistorySendingReadAndSse() throws Exception {
    long id=chat.open(court.getId(),guest.getEmail());
    mvc.perform(get("/api/messages/conversations")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/messages/events")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/messages/conversations/"+id).with(user(otherGuest.getEmail()).roles("CUSTOMER"))).andExpect(status().isForbidden());
    mvc.perform(get("/api/messages/conversations/"+id+"/messages").with(user(otherOwner.getEmail()).roles("OWNER"))).andExpect(status().isForbidden());
    String body=json.writeValueAsString(Map.of("clientId",uuid(),"content","hello","senderId",owner.getId()));
    mvc.perform(post("/api/messages/conversations/"+id+"/messages").with(user(guest.getEmail()).roles("CUSTOMER")).contentType("application/json").content(body)).andExpect(status().isForbidden());
    mvc.perform(post("/api/messages/conversations/"+id+"/messages").with(user(otherOwner.getEmail()).roles("OWNER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
    mvc.perform(post("/api/messages/conversations/"+id+"/messages").with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.senderId").value(guest.getId().intValue())).andExpect(jsonPath("$.senderRole").value("CUSTOMER"));
    mvc.perform(get("/owner/messages").with(user(guest.getEmail()).roles("CUSTOMER"))).andExpect(status().isForbidden());
    mvc.perform(get("/messages").param("conversation",Long.toString(id)).with(user(otherGuest.getEmail()).roles("CUSTOMER"))).andExpect(status().isForbidden());
    var incoming=chat.history(id,guest.getEmail(),0,0).messages().getFirst();
    mvc.perform(post("/api/messages/conversations/"+id+"/read").with(user(otherGuest.getEmail()).roles("CUSTOMER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("through",incoming.id())))).andExpect(status().isForbidden());
  }
  @Test void persistsPagedHistoryAndRealUnreadCounters(){
    long id=chat.open(court.getId(),guest.getEmail());
    for(int i=0;i<35;i++)chat.send(id,guest.getEmail(),uuid(),"Tin "+i);
    assertThat(chat.totalUnread(owner.getEmail())).isEqualTo(35);
    assertThat(chat.totalUnread(guest.getEmail())).isZero();
    var latest=chat.history(id,owner.getEmail(),0,0);assertThat(latest.messages()).hasSize(30);assertThat(latest.hasMore()).isTrue();
    var older=chat.history(id,owner.getEmail(),latest.messages().getFirst().id(),0);assertThat(older.messages()).hasSize(5);assertThat(older.hasMore()).isFalse();
    var last=latest.messages().getLast();chat.markRead(id,owner.getEmail(),last.id());assertThat(chat.totalUnread(owner.getEmail())).isZero();
    var answer=chat.send(id,owner.getEmail(),uuid(),"Chủ sân trả lời");assertThat(answer.senderName()).isEqualTo(owner.getName());assertThat(answer.sentAt()).endsWith("+07:00");
    assertThat(chat.totalUnread(guest.getEmail())).isEqualTo(1);
    assertThat(chat.history(id,guest.getEmail(),0,last.id()).messages()).extracting(CourtMessagingService.MessageView::content).containsExactly("Chủ sân trả lời");
  }
  @Test void retriesAreIdempotentEvenWithConcurrentRequests() throws Exception {
    long id=chat.open(court.getId(),guest.getEmail());String key=uuid();var executor=Executors.newFixedThreadPool(2);
    try {
      var results=executor.invokeAll(List.<Callable<CourtMessagingService.MessageView>>of(()->chat.send(id,guest.getEmail(),key,"Một tin"),()->chat.send(id,guest.getEmail(),key,"Một tin")));
      assertThat(results.get(0).get().id()).isEqualTo(results.get(1).get().id());
      assertThat(chat.history(id,guest.getEmail(),0,0).messages()).hasSize(1);
      assertThatThrownBy(()->chat.send(id,guest.getEmail(),key,"Nội dung khác")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }finally{executor.shutdownNow();}
  }
  @Test void rejectsLongMessagesAndRateLimitsAccount() throws Exception {
    long id=chat.open(court.getId(),guest.getEmail());
    String path="/api/messages/conversations/"+id+"/messages";
    mvc.perform(post(path).with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("clientId",uuid(),"content","x".repeat(1001))))).andExpect(status().isBadRequest());
    for(int i=0;i<30;i++)mvc.perform(post(path).with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("clientId",uuid(),"content","Tin giới hạn")))).andExpect(status().isOk());
    mvc.perform(post(path).with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("clientId",uuid(),"content","Vượt giới hạn")))).andExpect(status().isTooManyRequests());
  }
  record Browser(HttpClient http,String csrf,String header) {}
  Browser login(Account actor) throws Exception {
    var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var client=HttpClient.newBuilder().cookieHandler(cookies).build();
    String html=client.send(HttpRequest.newBuilder(URI.create(base()+"/login")).GET().build(),HttpResponse.BodyHandlers.ofString()).body();
    var matcher=Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);assertThat(matcher.find()).isTrue();String token=matcher.group(1);
    String form="username="+URLEncoder.encode(actor.getEmail(),StandardCharsets.UTF_8)+"&password="+password+"&_csrf="+URLEncoder.encode(token,StandardCharsets.UTF_8);
    var response=client.send(HttpRequest.newBuilder(URI.create(base()+"/login")).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(302);
    html=client.send(HttpRequest.newBuilder(URI.create(base()+"/messages")).GET().build(),HttpResponse.BodyHandlers.ofString()).body();
    var meta=Pattern.compile("<meta name=\"_csrf\" content=\"([^\"]+)\"").matcher(html);assertThat(meta.find()).isTrue();return new Browser(client,meta.group(1),"X-CSRF-TOKEN");
  }
  String base(){return "http://localhost:"+port;}
  InputStream subscribe(Browser client) throws Exception {
    var response=client.http.send(HttpRequest.newBuilder(URI.create(base()+"/api/messages/events")).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
    assertThat(response.statusCode()).isEqualTo(200);return response.body();
  }
  String event(BufferedReader reader) throws Exception {
    StringBuilder value=new StringBuilder();String line;while((line=reader.readLine())!=null){if(line.isEmpty()&&!value.isEmpty())break;value.append(line).append('\n');}return value.toString();
  }
  @Test void sseOnlyNotifiesParticipantsAfterCommitAndRecoversMissedMessages() throws Exception {
    long id=chat.open(court.getId(),guest.getEmail());var customer=login(guest);var landlord=login(owner);var stranger=login(otherGuest);
    var executor=Executors.newCachedThreadPool(task->{var thread=new Thread(task);thread.setDaemon(true);return thread;});
    try(var customerStream=subscribe(customer);var ownerStream=subscribe(landlord);var otherStream=subscribe(stranger)){
      var c=new BufferedReader(new InputStreamReader(customerStream,StandardCharsets.UTF_8));var o=new BufferedReader(new InputStreamReader(ownerStream,StandardCharsets.UTF_8));var x=new BufferedReader(new InputStreamReader(otherStream,StandardCharsets.UTF_8));
      assertThat(event(c)).contains("event:ready");assertThat(event(o)).contains("event:ready");assertThat(event(x)).contains("event:ready");
      var nextCustomer=executor.submit(()->event(c));var nextOwner=executor.submit(()->event(o));var outsider=executor.submit(()->event(x));
      var sent=chat.send(id,guest.getEmail(),uuid(),"Riêng tư");
      assertThat(nextCustomer.get(3,TimeUnit.SECONDS)).contains("event:message",Long.toString(id)).doesNotContain("Riêng tư");
      assertThat(nextOwner.get(3,TimeUnit.SECONDS)).contains("event:message");
      assertThat(messages.findById(sent.id())).isPresent();
      assertThatThrownBy(()->outsider.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      var noRollbackEvent=executor.submit(()->event(o));
      assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{chat.send(id,guest.getEmail(),uuid(),"Rollback");throw new IllegalStateException("test rollback");})).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(()->noRollbackEvent.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
      ownerStream.close();
      var missed=chat.send(id,guest.getEmail(),uuid(),"Tin khi mất kết nối");
      try(var reconnect=subscribe(landlord)){
        assertThat(event(new BufferedReader(new InputStreamReader(reconnect,StandardCharsets.UTF_8)))).contains("event:ready");
        assertThat(chat.history(id,owner.getEmail(),0,sent.id()).messages()).extracting(CourtMessagingService.MessageView::id).containsExactly(missed.id());
      }
      assertThat(event(c)).contains("event:message"); // consume the committed message sent while the owner was disconnected
      customer.http.send(HttpRequest.newBuilder(URI.create(base()+"/logout")).header(customer.header,customer.csrf).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding());
      var ended=executor.submit(()->{try{return event(c);}catch(IOException closed){return "";}});chat.send(id,owner.getEmail(),uuid(),"Sau đăng xuất");assertThat(ended.get(3,TimeUnit.SECONDS)).isEmpty();
      assertThat(customer.http.send(HttpRequest.newBuilder(URI.create(base()+"/api/messages/events")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }finally{executor.shutdownNow();}
  }
  @Test void directOwnerDirectoryAndConversationNeedNoCourt() throws Exception {
    var owners=chat.owners(guest.getEmail(),"",0);
    assertThat(owners.owners()).extracting(CourtMessagingService.OwnerView::id).contains(owner.getId(),otherOwner.getId());
    long id=chat.openOwner(owner.getId(),guest.getEmail());
    assertThat(chat.openOwner(owner.getId(),guest.getEmail())).isEqualTo(id);
    assertThat(chat.detail(id,guest.getEmail()).courtId()).isZero();
    assertThat(chat.detail(id,guest.getEmail()).peerName()).isEqualTo(owner.getName());
    mvc.perform(get("/api/messages/owners").with(user(guest.getEmail()).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(owner.getEmail()))));
    mvc.perform(post("/api/messages/owners/"+owner.getId()+"/conversation").with(user(guest.getEmail()).roles("CUSTOMER")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.id").value((int)id));
    mvc.perform(get("/api/messages/conversations/"+id).with(user(otherOwner.getEmail()).roles("OWNER"))).andExpect(status().isForbidden());
    assertThatThrownBy(()->chat.openOwner(guest.getId(),otherGuest.getEmail())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }
  @Test void humanChatNeverCallsAiEvenWithLegacyAskAi() {
    long id=chat.openFromCourt(court.getId(),guest.getEmail());
    assertThat(chat.detail(id,guest.getEmail()).courtId()).isEqualTo(court.getId());
    var sent=chat.send(id,guest.getEmail(),uuid(),"Hello owner",true);
    assertThat(messages.findByAiReplyTo(sent.id())).isEmpty();
    chat.send(id,owner.getEmail(),uuid(),"Hello customer");
    assertThat(chat.history(id,guest.getEmail(),0,0).messages()).hasSize(2)
        .allSatisfy(m->assertThat(m.senderRole()).isIn("CUSTOMER","OWNER"));
    org.mockito.Mockito.verify(ai,org.mockito.Mockito.never()).interpret(org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
  }
  @Test void unifiedAiSearchOnlyReturnsSelectedOwnersCourts() throws Exception {
    var intent=json.readTree("{\"action\":\"search\",\"sport\":\"Cầu lông\",\"area\":\"Thủ Đức\",\"date\":\""+java.time.LocalDate.now(AppConstants.ZONE).plusDays(1)+"\",\"hour\":19}");
    var reply=assistant.answer(intent,owner.getId());
    assertThat(reply.links()).hasSize(1);assertThat(reply.links().getFirst().url()).startsWith("/courts/"+court.getId()+"?");
    assertThat(reply.message()).doesNotContain(secondCourt.getName());
  }
  @Test void messagingPagesRenderWithSafeTextAndSnapshot() throws Exception {
    long id=chat.open(court.getId(),guest.getEmail());chat.send(id,guest.getEmail(),uuid(),"<img src=x onerror=alert(1)>");
    var result=mvc.perform(get("/messages").param("conversation",Long.toString(id)).with(user(guest.getEmail()).roles("CUSTOMER"))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("data-court-messages"))).andReturn();
    var ownerResult=mvc.perform(get("/owner/messages").with(user(owner.getEmail()).roles("OWNER"))).andExpect(status().isOk()).andReturn();
    assertThat(chat.history(id,owner.getEmail(),0,0).messages().getFirst().content()).isEqualTo("<img src=x onerror=alert(1)>");
    if(Boolean.getBoolean("sanNha.uiSnapshots")){Path dir=Path.of("target/ui-preview");Files.createDirectories(dir);Files.writeString(dir.resolve("messages.html"),result.getResponse().getContentAsString(StandardCharsets.UTF_8));Files.writeString(dir.resolve("messages-owner.html"),ownerResult.getResponse().getContentAsString(StandardCharsets.UTF_8));}
  }
}
