package vn.uth.court;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.*;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties={"app.demo.seed=false","GOOGLE_CLIENT_ID=test-client","GOOGLE_CLIENT_SECRET=test-secret"})
@ActiveProfiles("demo") @AutoConfigureMockMvc @Transactional
class AdminIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired AccountRepository accounts;
 @Autowired CourtRepository courts;
 @Autowired AdminControlRepository control;
 @Autowired AdminAuditRepository audit;
 @Autowired AdminAccountService admin;
 @Autowired BookingService booking;
 @Autowired CourtMessagingService chat;
 @Autowired GoogleOidcUserService google;
 @Autowired PasswordEncoder passwords;
 Account operator,owner1,owner2,customer;
 Court c1,c2;
 static final String PASSWORD="UniqueTestPassword123!";
 Account account(String email,String role){
  Account a=new Account();a.setName(role+" "+email.split("@")[0]);a.setEmail(email);a.setRole(role);
  a.setPassword(passwords.encode(PASSWORD));return accounts.saveAndFlush(a);
 }
 Court court(Account owner,String name){
  Court c=new Court();c.setOwner(owner);c.setName(name);c.setSport("Cầu lông");c.setArea("Thủ Đức");c.setAddress(name+" address");
  c.setPrice(new BigDecimal("100000"));c.setMinPrice(new BigDecimal("100000"));c.setOpenHour(6);c.setCloseHour(23);c.setActive(true);
  return courts.saveAndFlush(c);
 }
 @BeforeEach void fixture(){
  if(!control.existsById(1L))control.saveAndFlush(new AdminControl(1));
  operator=account("admin-case@test.vn","ADMIN");owner1=account("owner-one-case@test.vn","OWNER");
  owner2=account("owner-two-case@test.vn","OWNER");customer=account("customer-case@test.vn","CUSTOMER");
  customer.setPhone("0901112233");accounts.saveAndFlush(customer);
  c1=court(owner1,"OWNER_ONE_ONLY");c2=court(owner2,"OWNER_TWO_ONLY");
 }
 @Test void realDashboardFiltersAndViewsNeverExposePasswordHashes() throws Exception {
  var result=mvc.perform(get("/admin").with(user(operator.getEmail()).roles("ADMIN"))).andExpect(status().isOk()).andReturn();
  assertThat(result.getResponse().getContentAsString()).contains("Quản lý tài khoản").doesNotContain(operator.getPassword(),PASSWORD);
  var stats=(AdminAccountService.Stats)result.getModelAndView().getModel().get("stats");
  assertThat(stats.owners()).isEqualTo(2);assertThat(stats.customers()).isEqualTo(1);
  assertThat(admin.list(operator.getEmail(),"owner-one","OWNER","active",0).getTotalElements()).isEqualTo(1);
  assertThat(admin.list(operator.getEmail(),"","CUSTOMER","locked",0)).isEmpty();
  for(String path:List.of("/admin/accounts/"+owner1.getId(),"/admin/audit"))
   mvc.perform(get(path).with(user(operator.getEmail()).roles("ADMIN"))).andExpect(status().isOk());
 }
 @Test void onlyAdminCanReadAndMutateAdminEndpointsAndCsrfIsRequired() throws Exception {
  for(Account a:List.of(owner1,customer))
   mvc.perform(get("/admin").with(user(a.getEmail()).roles(a.getRole()))).andExpect(status().isForbidden());
  mvc.perform(post("/admin/accounts/"+customer.getId()+"/lock").param("locked","true")
    .with(user(operator.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
  mvc.perform(post("/admin/accounts/"+customer.getId()+"/lock").param("locked","true").with(csrf())
    .with(user(owner1.getEmail()).roles("OWNER"))).andExpect(status().isForbidden());
  assertThatThrownBy(()->admin.stats(customer.getEmail())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
 @Test void adminCannotCreateOwnerOrChangeAnyRole() throws Exception {
  var auth=user(operator.getEmail()).roles("ADMIN");
  mvc.perform(get("/owner").with(auth)).andExpect(status().isForbidden());
  mvc.perform(get("/admin/accounts/new").with(auth)).andExpect(status().isNotFound());
  mvc.perform(post("/admin/accounts").with(auth).with(csrf()).param("name","No owner").param("email","not-created@test.vn").param("initialPassword",PASSWORD)).andExpect(status().isMethodNotAllowed());
  mvc.perform(post("/admin/accounts/"+customer.getId()+"/role").with(auth).with(csrf()).param("role","ADMIN")).andExpect(status().isNotFound());
  assertThat(accounts.findByEmail("not-created@test.vn")).isEmpty();
  assertThat(customer.getRole()).isEqualTo("CUSTOMER");
  String html=mvc.perform(get("/admin/accounts/"+owner1.getId()).with(auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(html).doesNotContain("/role", "Cập nhật vai trò", "/admin/accounts/new", "Số sân liên kết");
 }
 @Test void cannotLockSelfAndOwnerRelationsRemainUnchanged() {
  assertThatThrownBy(()->admin.lock(operator.getEmail(),operator.getId(),true)).isInstanceOf(IllegalArgumentException.class);
  admin.lock(operator.getEmail(),owner1.getId(),true);
  assertThat(accounts.countByRoleAndLockedFalse("ADMIN")).isEqualTo(1);
  assertThat(owner1.getRole()).isEqualTo("OWNER");
  assertThat(courts.findById(c1.getId()).orElseThrow().getOwner().getId()).isEqualTo(owner1.getId());
 }
 @Test void lockBlocksFormGoogleAndExistingSessionEvenAfterUnlock() throws Exception {
  customer.setGoogleSub("locked-sub");accounts.saveAndFlush(customer);
  var login=mvc.perform(post("/login").with(csrf()).param("username",customer.getEmail()).param("password",PASSWORD)).andReturn();
  var original=(MockHttpSession)login.getRequest().getSession();
  var dormant=new MockHttpSession();
  dormant.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
    original.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
  dormant.setAttribute(AccountSessionFilter.VERSION,customer.getSecurityVersion());
  admin.lock(operator.getEmail(),customer.getId(),true);
  mvc.perform(post("/login").with(csrf()).param("username",customer.getEmail()).param("password",PASSWORD))
    .andExpect(redirectedUrl("/login?error"));
  var token=OidcIdToken.withTokenValue("test").subject("locked-sub").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
    .claim("email",customer.getEmail()).claim("email_verified",true).build();
  assertThatThrownBy(()->google.localPrincipal(new DefaultOidcUser(List.of(),token))).isInstanceOf(OAuth2AuthenticationException.class);
  mvc.perform(get("/account").session(original)).andExpect(redirectedUrl("/login?session_expired"));
  admin.lock(operator.getEmail(),customer.getId(),false);
  mvc.perform(get("/account").session(dormant)).andExpect(redirectedUrl("/login?session_expired"));
 }
 @Test void profileUpdateRevokesSessionsButCannotChangeRole() throws Exception {
  var login=mvc.perform(post("/login").with(csrf()).param("username",customer.getEmail()).param("password",PASSWORD)).andReturn();
  var session=(MockHttpSession)login.getRequest().getSession();
  mvc.perform(post("/admin/accounts/"+customer.getId()+"/profile").with(user(operator.getEmail()).roles("ADMIN")).with(csrf())
    .param("name","Updated Customer").param("email",customer.getEmail()).param("phone",customer.getPhone()).param("role","ADMIN"))
    .andExpect(status().is3xxRedirection());
  mvc.perform(get("/account").session(session)).andExpect(redirectedUrl("/login?session_expired"));
  assertThat(customer.getRole()).isEqualTo("CUSTOMER");
  assertThat(audit.findAll()).extracting(AdminAudit::getAction).contains("PROFILE_UPDATED");
 }
 @Test void ownerRoutesBookingsRevenueAndConversationIdsAreScoped() throws Exception {
  var a=user(owner1.getEmail()).roles("OWNER");
  mvc.perform(get("/owner").with(a)).andExpect(status().isOk())
    .andExpect(content().string(org.hamcrest.Matchers.containsString("OWNER_ONE_ONLY")))
    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("OWNER_TWO_ONLY"))));
  mvc.perform(get("/owner/courts/"+c2.getId()+"/edit").with(a)).andExpect(status().isForbidden());
  mvc.perform(post("/owner/courts/save").with(a).with(csrf()).param("id",c2.getId().toString()).param("name",""))
    .andExpect(status().isForbidden());
  mvc.perform(post("/owner/courts/"+c2.getId()+"/block").with(a).with(csrf())
    .param("date",booking.today().plusDays(1).toString()).param("hour","19")).andExpect(status().isForbidden());
  var foreignBooking=booking.book(c2.getId(),booking.today().plusDays(1),19,customer);
  mvc.perform(get("/owner/bookings/"+foreignBooking.getId()).with(a)).andExpect(status().isForbidden());
  mvc.perform(get("/owner").with(a).param("courtId",c2.getId().toString())).andExpect(status().isForbidden());
  long thread=chat.openOwner(owner2.getId(),customer.getEmail());
  mvc.perform(get("/api/messages/conversations/"+thread).with(a)).andExpect(status().isForbidden());
  mvc.perform(post("/api/messages/conversations/"+thread+"/messages").with(a).with(csrf())
    .contentType("application/json").content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("clientId",UUID.randomUUID().toString(),"content","cross-account")))).andExpect(status().isForbidden());
  assertThatThrownBy(()->chat.send(thread,owner1.getEmail(),UUID.randomUUID().toString(),"cross-account",false))
    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
 }
 @Test void loginRoutesByRoleAndPublicRegistrationCannotSelectAdmin() throws Exception {
  for(Account a:List.of(operator,owner1,customer))
    mvc.perform(post("/login").with(csrf()).param("username",a.getEmail()).param("password",PASSWORD))
      .andExpect(redirectedUrl(a.isAdmin()?"/admin":a.isOwner()?"/owner":"/"));
  mvc.perform(post("/register").with(csrf()).param("name","New Customer").param("email","public-role@test.vn")
      .param("phone","0902223344").param("password","Password123!").param("confirm","Password123!").param("role","ADMIN"));
  assertThat(accounts.findByEmail("public-role@test.vn")).isPresent();
  assertThat(accounts.findByEmail("public-role@test.vn").orElseThrow().getRole()).isEqualTo("CUSTOMER");
 }
 @Test void firstAdminBootstrapDoesNotOverwriteAnExistingAccount() {
  accounts.delete(operator);accounts.flush();
  assertThatThrownBy(()->admin.bootstrap("Admin",customer.getEmail(),"",PASSWORD)).isInstanceOf(IllegalArgumentException.class);
  long id=admin.bootstrap("First Admin","first-admin@test.vn","",PASSWORD);
  assertThat(accounts.findById(id).orElseThrow().isAdmin()).isTrue();
  assertThatThrownBy(()->admin.bootstrap("Second Admin","second-admin@test.vn","",PASSWORD)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void profileUpdateEscapesXssAndPaginates() throws Exception {
  admin.update(operator.getEmail(),customer.getId(),"<script>alert(1)</script>",customer.getEmail(),"");
  var detail=mvc.perform(get("/admin/accounts/"+customer.getId()).with(user(operator.getEmail()).roles("ADMIN")))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertThat(detail).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;");
  for(int i=0;i<16;i++)account("page-"+i+"@test.vn","CUSTOMER");
  var page=admin.list(operator.getEmail(),"","CUSTOMER","",0);
  assertThat(page.getSize()).isEqualTo(15);assertThat(page.hasNext()).isTrue();
 }
 @Test void adminUiSnapshots() throws Exception {
  if(!Boolean.getBoolean("sanNha.uiSnapshots"))return;
  var dir=Path.of("target/ui-preview");Files.createDirectories(dir);
  var paths=Map.of("admin-list","/admin","admin-detail","/admin/accounts/"+owner1.getId(),"admin-audit","/admin/audit");
  for(var e:paths.entrySet()){
   String html=mvc.perform(get(e.getValue()).with(user(operator.getEmail()).roles("ADMIN")))
     .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
   Files.writeString(dir.resolve(e.getKey()+".html"),html);
  }
 }
 @Test void googleEmailsAndExistingRolesCannotBeSilentlyReassigned() {
  customer.setGoogleSub("profile-sub");accounts.saveAndFlush(customer);
  assertThatThrownBy(()->admin.update(operator.getEmail(),customer.getId(),"Changed","other@test.vn","")).isInstanceOf(IllegalArgumentException.class);
  assertThat(customer.getRole()).isEqualTo("CUSTOMER");
 }

}
