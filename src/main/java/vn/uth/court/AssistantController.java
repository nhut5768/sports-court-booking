package vn.uth.court;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
  public record Message(@NotBlank @Size(max=1000) String message) {}
  private static class Conversation {
    final List<AiIntentClient.Turn> history = new ArrayList<>();
    final AtomicBoolean busy = new AtomicBoolean();
    long window = System.currentTimeMillis(), last = window;
    int requests;
  }
  private final AiIntentClient ai;
  private final CourtAssistantService courts;
  private final Semaphore concurrent = new Semaphore(4);
  private long globalWindow = System.currentTimeMillis();
  private int globalRequests;
  public AssistantController(AiIntentClient ai,CourtAssistantService courts) { this.ai=ai; this.courts=courts; }
  @GetMapping("/status") public Map<String,Boolean> status() { return Map.of("available",ai.available()); }

  @PostMapping("/messages")
  public ResponseEntity<?> message(@Valid @RequestBody Message input,HttpSession session) {
    if (!ai.available()) return error(503,"Trợ lý chưa khả dụng vì máy chủ chưa cấu hình AI hợp lệ. Bạn vẫn có thể tìm sân trên website.");
    Conversation state;
    synchronized (session) {
      state = (Conversation) session.getAttribute("courtAssistant");
      if (state == null) { state = new Conversation(); session.setAttribute("courtAssistant",state); }
    }
    if (!state.busy.compareAndSet(false,true)) return error(429,"Trợ lý đang trả lời tin trước. Vui lòng đợi một chút.");
    boolean acquired = false;
    try {
      long now = System.currentTimeMillis();
      if (now-state.window >= 60000) { state.window=now; state.requests=0; }
      if (++state.requests > 8 || !globalPermit(now)) return error(429,"Bạn gửi hơi nhanh. Vui lòng chờ một phút rồi thử lại.");
      if (now-state.last > 20*60000) state.history.clear();
      state.last = now;
      if (!(acquired=concurrent.tryAcquire())) return error(429,"Trợ lý đang bận. Bạn vui lòng thử lại sau ít phút.");
      var intent = ai.interpret(List.copyOf(state.history),input.message().trim(),courts.today());
      var answer = courts.answer(intent);
      state.history.add(new AiIntentClient.Turn("user",input.message().trim()));
      state.history.add(new AiIntentClient.Turn("assistant",answer.message().substring(0,Math.min(1200,answer.message().length()))));
      while (state.history.size()>8) state.history.remove(0);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(answer);
    } catch (AiUnavailableException ex) {
      return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
          .body(Map.of("message",ex.getMessage(),"code",ex.reason().name()));
    } finally { if (acquired) concurrent.release(); state.busy.set(false); }
  }
  private synchronized boolean globalPermit(long now) {
    if (now-globalWindow>=60000) { globalWindow=now; globalRequests=0; }
    return ++globalRequests <= 40;
  }
  @DeleteMapping("/history") public ResponseEntity<?> clear(HttpSession session) {
    Conversation state;
    synchronized(session) { state = (Conversation) session.getAttribute("courtAssistant"); }
    if (state != null) {
      if (!state.busy.compareAndSet(false,true)) return error(429,"Vui lòng đợi trợ lý trả lời xong trước khi xóa.");
      try { state.history.clear(); } finally { state.busy.set(false); }
    }
    // Preserve request counters: clearing history must not bypass rate limits.
    return ResponseEntity.noContent().build();
  }
  private ResponseEntity<?> error(int code,String message) {
    var response=ResponseEntity.status(code).cacheControl(CacheControl.noStore());
    if (code==429) response.header("Retry-After","60");
    return response.body(Map.of("message",message));
  }
}
