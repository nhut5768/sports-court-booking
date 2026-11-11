package vn.uth.court;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class CourtMessagesPage {
  private final CourtMessagingService chat;
  public CourtMessagesPage(CourtMessagingService chat){this.chat=chat;}
  @PostMapping("/messages/courts/{courtId}")
  String open(@PathVariable long courtId,Principal p){return "redirect:/messages?conversation="+chat.openFromCourt(courtId,p.getName());}
  @GetMapping({"/messages","/owner/messages"})
  String messages(@RequestParam(required=false) Long conversation,Principal p,Model model){
    var actor=chat.actor(p.getName());
    if(conversation!=null)chat.detail(conversation,p.getName());
    model.addAttribute("chatAccountId",actor.getId());model.addAttribute("selectedConversation",conversation);
    return "messages";
  }
}
