package vn.uth.court;
import org.springframework.context.annotation.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.context.ConfigurableApplicationContext;
@Configuration
@ConditionalOnProperty(name="app.admin.bootstrap",havingValue="true")
public class AdminBootstrapCommand {
 @Bean CommandLineRunner firstAdmin(AdminAccountService service,Environment env,ConfigurableApplicationContext context){
  return args -> {
   var active=java.util.Arrays.asList(env.getActiveProfiles());
   String url=env.getProperty("spring.datasource.url","");
   if(active.contains("shared")||active.contains("prod")||active.contains("render")||
       !(url.startsWith("jdbc:mysql://localhost:")||url.startsWith("jdbc:mysql://127.0.0.1:")))
      throw new IllegalStateException("Chỉ khởi tạo admin một lần trên MySQL local.");
   service.bootstrap(env.getProperty("ADMIN_NAME","Quản trị Sân Nhà"),
       env.getRequiredProperty("ADMIN_EMAIL"),env.getProperty("ADMIN_PHONE"),env.getRequiredProperty("ADMIN_PASSWORD"));
   System.out.println("Đã khởi tạo quản trị viên đầu tiên. Không hiển thị mật khẩu.");
   System.exit(SpringApplication.exit(context,()->0));
  };
 }
}
