package vn.uth.court;
import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.access.AccessDeniedException;

@SpringBootTest(properties={"app.demo.seed=false",
 "spring.datasource.url=jdbc:h2:mem:admin-races;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("demo")
class LastAdminConcurrencyTest {
 @Autowired AccountRepository accounts;
 @Autowired AdminControlRepository control;
 @Autowired AdminAuditRepository audit;
 @Autowired AdminAccountService admin;
 Account a,b;
 Account make(String email){
  Account v=new Account();v.setName("Race admin");v.setRole("ADMIN");v.setEmail(email);v.setPassword("{unusable-test-password}");
  return accounts.saveAndFlush(v);
 }
 @BeforeEach void fixture(){
  audit.deleteAll();accounts.deleteAll();
  if(!control.existsById(1L))control.saveAndFlush(new AdminControl(1));
  a=make("race-a@test.vn");b=make("race-b@test.vn");
 }
 @org.junit.jupiter.api.Test
 void concurrentAdminActionsAlwaysLeaveOneActiveAdmin()throws Exception{
  var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try {
   var first=pool.submit(()->attempt(a,b,start));
   var second=pool.submit(()->attempt(b,a,start));
   start.countDown();
   int successes=(first.get(10,TimeUnit.SECONDS)?1:0)+(second.get(10,TimeUnit.SECONDS)?1:0);
   assertThat(successes).isEqualTo(1);
   assertThat(accounts.countByRoleAndLockedFalse("ADMIN")).isEqualTo(1);
   assertThat(audit.count()).isEqualTo(1);
  }finally{pool.shutdownNow();}
 }
 boolean attempt(Account actor,Account target,CountDownLatch start)throws Exception{
  start.await(5,TimeUnit.SECONDS);
  try{
   admin.lock(actor.getEmail(),target.getId(),true);
   return true;
  }catch(IllegalArgumentException|AccessDeniedException expected){return false;}
 }
}
