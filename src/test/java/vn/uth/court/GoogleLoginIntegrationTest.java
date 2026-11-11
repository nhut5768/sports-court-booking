package vn.uth.court;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@SpringBootTest(properties={"app.demo.seed=false","GOOGLE_CLIENT_ID=test-client","GOOGLE_CLIENT_SECRET=test-secret"})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
@Transactional
class GoogleLoginIntegrationTest {
  @Autowired GoogleOidcUserService users;
  @Autowired AccountRepository accounts;
  @Autowired MockMvc mvc;
  OidcUser identity(String sub, String email, boolean verified) {
    return new DefaultOidcUser(List.of(), OidcIdToken.withTokenValue("test")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).subject(sub)
        .claim("email",email).claim("email_verified",verified).claim("name","Customer").build());
  }
  @Test void createsCustomerAndReusesSubject() {
    var p=users.localPrincipal(identity("sub1","google@test.vn",true));
    var a=accounts.findByGoogleSub("sub1").orElseThrow();
    assertThat(a.getRole()).isEqualTo("CUSTOMER");
    assertThat(a.getPhone()).isNull();
    assertThat(a.getMustChangePassword()).isFalse();
    assertThat(p.getName()).isEqualTo(a.getEmail());
    assertThat(p.getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
    assertThat(users.localPrincipal(identity("sub1","changed@test.vn",true)).getName())
        .isEqualTo("google@test.vn");
    assertThat(accounts.findByGoogleSub("sub1").orElseThrow().getId()).isEqualTo(a.getId());
  }
  @Test void refusesLockedOrPrivilegedGoogleAccount() {
    users.localPrincipal(identity("restricted","restricted@test.vn",true));
    var a=accounts.findByGoogleSub("restricted").orElseThrow();
    a.setLocked(true);accounts.saveAndFlush(a);
    assertThatThrownBy(()->users.localPrincipal(identity("restricted",a.getEmail(),true)))
        .isInstanceOf(OAuth2AuthenticationException.class);
    a.setLocked(false);a.setRole("OWNER");accounts.saveAndFlush(a);
    assertThatThrownBy(()->users.localPrincipal(identity("restricted",a.getEmail(),true)))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }
  @Test void googleCustomerCanAccessMessaging() throws Exception {
    var principal=users.localPrincipal(identity("chat-google","chat-google@test.vn",true));
    mvc.perform(get("/api/messages/conversations").with(
        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(
          new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(principal,principal.getAuthorities(),"google"))))
        .andExpect(status().isOk());
  }
  @Test void localCallbackUsesPort8082() throws Exception {
    mvc.perform(get("http://localhost:8082/oauth2/authorization/google"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location",org.hamcrest.Matchers.containsString(
          "redirect_uri=http://localhost:8082/login/oauth2/code/google")));
  }
  @Test void refusesExistingOwnerEmail() {
    Account a=new Account(); a.setName("Owner"); a.setEmail("existing@test.vn");
    a.setPassword("unused"); a.setRole("OWNER"); accounts.saveAndFlush(a);
    assertThatThrownBy(()->users.localPrincipal(identity("attack","EXISTING@test.vn",true)))
        .isInstanceOf(OAuth2AuthenticationException.class);
    assertThat(a.getGoogleSub()).isNull();
    assertThat(accounts.findByGoogleSub("attack")).isEmpty();
  }
  @Test void refusesUnverifiedEmail() {
    assertThatThrownBy(()->users.localPrincipal(identity("bad","bad@test.vn",false)))
        .isInstanceOf(OAuth2AuthenticationException.class);
    assertThat(accounts.findByGoogleSub("bad")).isEmpty();
  }
  @Test void redirectsToRealGoogleAccountChooser() throws Exception {
    mvc.perform(get("/login")).andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/oauth2/authorization/google")));
    mvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location",org.hamcrest.Matchers.allOf(
          org.hamcrest.Matchers.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"),
          org.hamcrest.Matchers.containsString("prompt=select_account"),
          org.hamcrest.Matchers.containsString("state="),
          org.hamcrest.Matchers.containsString("nonce="))));
  }
  @Test void passwordLoginRemainsAvailableWithGoogleEnabled() throws Exception {
    Account local = new Account();
    local.setName("Local"); local.setEmail("local-google-test@test.vn");
    local.setRole("CUSTOMER");
    var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    local.setPassword(encoder.encode("Password123"));
    accounts.saveAndFlush(local);
    mvc.perform(post("/login")
        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
        .param("username", local.getEmail()).param("password", "Password123"))
        .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
  }
  @Test void googleAccountHasNoUsablePassword() {
    users.localPrincipal(identity("no-password", "no-password@test.vn", true));
    var a = accounts.findByGoogleSub("no-password").orElseThrow();
    var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    assertThat(encoder.matches(a.getPassword(), a.getPassword())).isFalse();
  }
  @Test void callbackWithoutStateFails() throws Exception {
    mvc.perform(get("/login/oauth2/code/google").param("code","fake").param("state","fake"))
        .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?google_error=failed"));
  }
}
