package vn.uth.court;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Per-account SSE channels, not client-selected topics. Events contain IDs only. */
@Component
public class CourtChatEvents {
  private record Subscription(long accountId,String email,long version,HttpSession session,SseEmitter emitter) {}
  private final Set<Subscription> subscribers=ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor(task->{
    Thread thread=new Thread(task,"court-chat-heartbeat");thread.setDaemon(true);return thread;
  });
  private final AccountRepository accounts;
  public CourtChatEvents(AccountRepository accounts) { this.accounts=accounts; heartbeat.scheduleAtFixedRate(this::ping,15,15,TimeUnit.SECONDS); }
  public synchronized SseEmitter subscribe(Account account,HttpSession session) {
    if(subscribers.size()>=2000||subscribers.stream().filter(s->s.accountId==account.getId()).count()>=3)
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Đã mở quá nhiều cửa sổ tin nhắn.");
    var emitter=new SseEmitter(90000L);var subscription=new Subscription(account.getId(),account.getEmail(),account.getSecurityVersion(),session,emitter);
    if(!valid(subscription))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập lại.");
    subscribers.add(subscription);
    emitter.onCompletion(()->subscribers.remove(subscription));
    emitter.onTimeout(()->{subscribers.remove(subscription);emitter.complete();});
    emitter.onError(error->subscribers.remove(subscription));
    send(subscription,"ready",Map.of("connected",true));return emitter;
  }
  private boolean valid(Subscription s) {
    try {
      var account=accounts.findById(s.accountId).orElse(null);
      if(account==null||account.isLocked()||account.getSecurityVersion()!=s.version)return false;
      var stored=s.session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
      return stored instanceof SecurityContext context && context.getAuthentication()!=null
          && context.getAuthentication().isAuthenticated() && s.email.equals(context.getAuthentication().getName());
    } catch(RuntimeException ex) { return false; }
  }
  @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
  public void committed(CourtMessagingService.Changed event) {
    for(var s:subscribers) if(s.accountId==event.customerId()||s.accountId==event.ownerId())
      send(s,"message",Map.of("conversationId",event.conversationId()));
  }
  private void ping() { for(var s:subscribers)send(s,"heartbeat",Map.of("connected",true)); }
  private void send(Subscription s,String type,Object data) {
    if(!valid(s)){close(s);return;}
    try { s.emitter.send(SseEmitter.event().name(type).data(data)); }
    catch(IOException|IllegalStateException ex){close(s);}
  }
  /** complete() throws once the container has failed the connection; one dead client must not stop delivery to the rest. */
  private void close(Subscription s) {
    subscribers.remove(s);
    try { s.emitter.complete(); } catch(IllegalStateException alreadyFailed) { }
  }
  @PreDestroy public void stop() { heartbeat.shutdownNow();for(var s:subscribers)close(s); }
}
