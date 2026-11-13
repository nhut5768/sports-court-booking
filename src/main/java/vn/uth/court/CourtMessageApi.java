package vn.uth.court;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController @RequestMapping("/api/messages")
public class CourtMessageApi {
  public record Send(@NotBlank @Size(max=36) String clientId,@NotBlank @Size(max=1000) String content,boolean askAi) {}
  public record Read(@Positive long through) {}
  private final CourtMessagingService chat;
  private final CourtChatEvents events;
  private final CourtChatRateLimit rate;
  public CourtMessageApi(CourtMessagingService chat,CourtChatEvents events,CourtChatRateLimit rate) {this.chat=chat;this.events=events;this.rate=rate;}
  @GetMapping("/owners") public ResponseEntity<?> owners(Principal p,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page){return ok(chat.owners(p.getName(),q,page));}
  @PostMapping("/owners/{id}/conversation") public ResponseEntity<?> open(@PathVariable long id,Principal p){rate.require(chat.actor(p.getName()).getId());return ok(java.util.Map.of("id",chat.openOwner(id,p.getName())));}
  @GetMapping("/conversations") public ResponseEntity<?> list(Principal p,@RequestParam(defaultValue="0") int page) {
    return ok(chat.inbox(p.getName(),page));
  }
  @GetMapping("/conversations/{id}") public ResponseEntity<?> thread(@PathVariable long id,Principal p) {return ok(chat.detail(id,p.getName()));}
  @GetMapping("/conversations/{id}/messages") public ResponseEntity<?> history(@PathVariable long id,Principal p,
      @RequestParam(defaultValue="0") long before,@RequestParam(defaultValue="0") long after) {return ok(chat.history(id,p.getName(),before,after));}
  @PostMapping("/conversations/{id}/messages") public ResponseEntity<?> send(@PathVariable long id,Principal p,@Valid @RequestBody Send input) {
    rate.require(chat.actor(p.getName()).getId());return ok(chat.send(id,p.getName(),input.clientId(),input.content(),input.askAi()));
  }
  @PostMapping("/conversations/{id}/read") public ResponseEntity<?> read(@PathVariable long id,Principal p,@Valid @RequestBody Read input) {
    chat.markRead(id,p.getName(),input.through());return ok(java.util.Map.of("saved",true));
  }
  @GetMapping(value="/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(Principal p,HttpServletRequest request,HttpServletResponse response) {
    response.setHeader("Cache-Control","no-store");response.setHeader("X-Accel-Buffering","no");
    return events.subscribe(chat.actor(p.getName()),request.getSession());
  }
  private ResponseEntity<?> ok(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
