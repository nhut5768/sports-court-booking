package vn.uth.court;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="app.demo.seed=false")
@ActiveProfiles("demo") @AutoConfigureMockMvc @Transactional
class RegistrationFlowsTest {
 @Autowired MockMvc mvc;
 @Autowired AccountRepository accounts;
 @Autowired CourtRepository courts;
 static final String PASS="OwnerSelfSet123!";
 @ParameterizedTest @ValueSource(strings={"ADMIN","CUSTOMER","OWNER"})
 void ownerRegistrationIgnoresClientRoleAndGoesStraightToOwnerAfterLogin(String requested)throws Exception{
  String email="owner-"+requested.toLowerCase()+"@signup.test";
  mvc.perform(post("/register/owner").with(csrf()).param("name","Independent Owner").param("email",email)
    .param("phone","0904445566").param("password",PASS).param("role",requested).param("mustChangePassword","true"))
    .andExpect(redirectedUrl("/login?owner_registered"));
  var a=accounts.findByEmail(email).orElseThrow();
  assertThat(a.getRole()).isEqualTo("OWNER");assertThat(a.getMustChangePassword()).isFalse();assertThat(a.isLocked()).isFalse();
  assertThat(courts.countByOwnerId(a.getId())).isZero();
  var login=mvc.perform(post("/login").with(csrf()).param("username",email).param("password",PASS))
    .andExpect(redirectedUrl("/owner")).andReturn();
  mvc.perform(get("/owner").session((org.springframework.mock.web.MockHttpSession)login.getRequest().getSession()))
    .andExpect(status().isOk());
 }
 @ParameterizedTest @ValueSource(strings={"/register","/register/customer"})
 void customerRegistrationCannotRequestAdminOrOwner(String path)throws Exception{
  mvc.perform(post(path).with(csrf()).param("name","Customer").param("email","customer@signup.test")
    .param("phone","0905556677").param("password",PASS).param("role","ADMIN"))
    .andExpect(redirectedUrl("/login?registered"));
  assertThat(accounts.findByEmail("customer@signup.test").orElseThrow().getRole()).isEqualTo("CUSTOMER");
  mvc.perform(post("/login").with(csrf()).param("username","customer@signup.test").param("password",PASS)).andExpect(redirectedUrl("/"));
 }
 @Test void emailUniquenessIsGlobalAndNeverPromotesAnExistingCustomerOrGoogleAccount()throws Exception{
  mvc.perform(post("/register").with(csrf()).param("name","Existing Customer").param("email","existing@signup.test")
    .param("phone","0906667788").param("password",PASS)).andExpect(status().is3xxRedirection());
  var original=accounts.findByEmail("existing@signup.test").orElseThrow();original.setGoogleSub("linked-sub");accounts.saveAndFlush(original);
  String hash=original.getPassword();
  mvc.perform(post("/register/owner").with(csrf()).param("name","Other Owner").param("email","EXISTING@signup.test")
    .param("phone","0907778899").param("password",PASS).param("role","OWNER")).andExpect(status().isOk()).andExpect(view().name("register-owner"));
  assertThat(accounts.findByEmail("existing@signup.test").orElseThrow().getRole()).isEqualTo("CUSTOMER");
  assertThat(original.getPassword()).isEqualTo(hash);assertThat(accounts.count()).isEqualTo(1);
 }
 @Test void missingCsrfCannotCreateEitherAccountAndNoAdminSignupExists()throws Exception{
  for(String path:List.of("/register","/register/owner"))
    mvc.perform(post(path).param("name","Blocked").param("email","blocked@signup.test").param("phone","0901112233").param("password",PASS))
      .andExpect(status().isForbidden());
  assertThat(accounts.countByRole("ADMIN")).isZero();
 }
 @Test void twoSelfRegisteredOwnersAreIndependent()throws Exception{
  int i=0;
  for(String email:List.of("first@signup.test","second@signup.test")){
   mvc.perform(post("/register/owner").with(csrf()).param("name","Owner "+i).param("email",email)
     .param("phone",i++==0?"0908889900":"0909990011").param("password",PASS)).andExpect(status().is3xxRedirection());
  }
  var one=accounts.findByEmail("first@signup.test").orElseThrow();
  var two=accounts.findByEmail("second@signup.test").orElseThrow();
  org.springframework.mock.web.MockHttpSession firstSession=null;
  for(var owner:List.of(one,two)){
   var login=mvc.perform(post("/login").with(csrf()).param("username",owner.getEmail()).param("password",PASS)).andReturn();
   var session=(org.springframework.mock.web.MockHttpSession)login.getRequest().getSession();
   if(owner==one)firstSession=session;
   for(int n=0;n<(owner==one?2:1);n++)mvc.perform(post("/owner/courts/save").session(session).with(csrf())
      .param("name","Court "+owner.getId()+" "+n).param("sport","Cầu lông").param("area","Thủ Đức").param("address","Địa chỉ sân")
      .param("price","100000").param("openHour","6").param("closeHour","22").param("active","true").param("ownerId",one.getId().toString()))
      .andExpect(redirectedUrl("/owner"));
  }
  assertThat(courts.countByOwnerId(one.getId())).isEqualTo(2);
  assertThat(courts.countByOwnerId(two.getId())).isEqualTo(1);
  long foreign=courts.findByOwnerIdOrderByIdDesc(two.getId()).getFirst().getId();
  mvc.perform(get("/owner/courts/"+foreign+"/edit").session(firstSession)).andExpect(status().isForbidden());
  assertThat(accounts.countByRole("OWNER")).isEqualTo(2);
  assertThat(accounts.findByEmail("first@signup.test").orElseThrow().getId())
      .isNotEqualTo(accounts.findByEmail("second@signup.test").orElseThrow().getId());
 }
 @Test void registrationPagesArePublicAndHaveSeparateFormActions()throws Exception{
  for(String path:List.of("/register/customer","/register/owner","/login")){
   var result=mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
   assertThat(result.getResponse().getContentAsString()).contains("/register/owner");
   if(Boolean.getBoolean("sanNha.uiSnapshots")){
    Files.createDirectories(Path.of("target/ui-preview"));
    Files.writeString(Path.of("target/ui-preview",path.equals("/register/owner")?"register-owner.html":path.equals("/login")?"login.html":"register.html"),result.getResponse().getContentAsString());
   }
  }
 }
}
