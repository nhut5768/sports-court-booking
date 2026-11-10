package vn.uth.court;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Tài khoản đang dùng mật khẩu tạm chỉ được vào trang đổi mật khẩu cho đến khi đổi xong. */
@Configuration
public class PasswordChangeInterceptor implements HandlerInterceptor, WebMvcConfigurer {
  private final AccountRepository accounts;

  public PasswordChangeInterceptor(AccountRepository accounts) {
    this.accounts = accounts;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(this)
        .excludePathPatterns(
            "/account/password", "/logout", "/css/**", "/vendor/**", "/images/**", "/media/**", "/error");
  }

  @Override
  public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
      throws Exception {
    Principal p = req.getUserPrincipal();
    if (p != null
        && accounts.findByEmail(p.getName()).map(Account::getMustChangePassword).orElse(false)) {
      res.sendRedirect(req.getContextPath() + "/account/password");
      return false;
    }
    return true;
  }
}
