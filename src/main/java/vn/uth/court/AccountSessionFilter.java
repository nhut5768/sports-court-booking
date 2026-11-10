package vn.uth.court;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

/** Checks persisted lock, role and revocation version on every authenticated request. */
public class AccountSessionFilter extends OncePerRequestFilter {
 public static final String VERSION="sanNhaAccountVersion";
 private final AccountRepository accounts;
 public AccountSessionFilter(AccountRepository accounts){this.accounts=accounts;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
  var auth=SecurityContextHolder.getContext().getAuthentication();
  if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken)){
   var a=accounts.findByEmail(auth.getName()).orElse(null);
   var session=req.getSession(false);
   Object version=session==null?null:session.getAttribute(VERSION);
   boolean role=a!=null&&auth.getAuthorities().stream().anyMatch(g->g.getAuthority().equals("ROLE_"+a.getRole()));
   if(a==null||a.isLocked()||!role||(version!=null&&!Long.valueOf(a.getSecurityVersion()).equals(version))){
    SecurityContextHolder.clearContext();
    if(session!=null)session.invalidate();
    res.setHeader("Cache-Control","no-store");
    if(req.getRequestURI().startsWith(req.getContextPath()+"/api/"))res.setStatus(401);
    else res.sendRedirect(req.getContextPath()+"/login?session_expired");
    return;
   }
   req.getSession().setAttribute(VERSION,a.getSecurityVersion());
  }
  chain.doFilter(req,res);
 }
}
