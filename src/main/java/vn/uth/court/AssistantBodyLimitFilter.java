package vn.uth.court;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20)
public class AssistantBodyLimitFilter extends OncePerRequestFilter {
  @Override protected boolean shouldNotFilter(HttpServletRequest request) {
    String path=request.getRequestURI().substring(request.getContextPath().length());
    return !"POST".equals(request.getMethod()) || !("/api/assistant/messages".equals(path) || path.startsWith("/api/messages/"));
  }
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
      throws ServletException, IOException {
    byte[] body = request.getInputStream().readNBytes(8193);
    if (body.length > 8192) {
      response.setStatus(413); response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
      response.setHeader("Cache-Control","no-store");
      response.getWriter().write("{\"message\":\"Tin nhắn quá dài. Vui lòng gửi tối đa 1.000 ký tự.\"}");return;
    }
    chain.doFilter(new HttpServletRequestWrapper(request) {
      @Override public ServletInputStream getInputStream() {
        var stream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
          @Override public int read() { return stream.read(); }
          @Override public boolean isFinished() { return stream.available()==0; }
          @Override public boolean isReady() { return true; }
          @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
        };
      }
      @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
    },response);
  }
}
