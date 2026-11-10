package vn.uth.court;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CourtChatRateLimit {
  private static class Window { long start=System.currentTimeMillis();int count; }
  private final Map<Long,Window> users=new HashMap<>();
  private final Window global=new Window();
  public synchronized void require(long accountId) {
    long now=System.currentTimeMillis();
    users.values().removeIf(w->now-w.start>60000);
    var user=users.computeIfAbsent(accountId,id->new Window());
    if(now-global.start>60000){global.start=now;global.count=0;}
    if(++user.count>30||++global.count>300)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Bạn gửi hơi nhanh. Vui lòng đợi một phút rồi thử lại.");
  }
}
