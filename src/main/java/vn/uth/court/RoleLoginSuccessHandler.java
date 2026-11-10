package vn.uth.court;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
@Component
public class RoleLoginSuccessHandler implements AuthenticationSuccessHandler {
 private final AccountRepository accounts;
 public RoleLoginSuccessHandler(AccountRepository accounts){this.accounts=accounts;}
 @Override public void onAuthenticationSuccess(HttpServletRequest req,HttpServletResponse res,Authentication auth)throws IOException {
  var a=accounts.findByEmail(auth.getName()).orElse(null);
  if(a==null||a.isLocked()){
   org.springframework.security.core.context.SecurityContextHolder.clearContext();
   req.getSession().invalidate();res.sendRedirect(req.getContextPath()+"/login?error");return;
  }
  req.getSession().setAttribute(AccountSessionFilter.VERSION,a.getSecurityVersion());
  String path=a.getMustChangePassword()?"/account/password":a.isAdmin()?"/admin":a.isOwner()?"/owner":"/";
  res.sendRedirect(req.getContextPath()+path);
 }
}
