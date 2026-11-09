package vn.uth.court;

import java.time.*;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourtMessagingService {
  public record MessageView(long id,long senderId,String senderName,String senderRole,String content,String clientId,String sentAt,String aiStatus,List<CourtAssistantService.Link> links) {}
  public record ThreadView(long id,long courtId,String courtName,String peerName,String peerRole,long unread,String updatedAt,boolean aiPaused) {}
  public record History(List<MessageView> messages,boolean hasMore) {}
  public record Inbox(List<ThreadView> conversations,boolean hasMore,long unread) {}
  public record AiRequested(long conversationId,long sourceId,long customerId) {}
  public record OwnerView(long id,String name,List<String> courts) {}
  public record Owners(List<OwnerView> owners,boolean hasMore) {}
  public record AiContext(long ownerId,List<AiIntentClient.Turn> history,String message) {}
  private final com.fasterxml.jackson.databind.ObjectMapper json;
  public record Changed(long conversationId,long customerId,long ownerId) {}
  private final CourtConversationRepository conversations;
  private final CourtChatMessageRepository messages;
  private final CourtRepository courts;
  private final AccountRepository accounts;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  public CourtMessagingService(CourtConversationRepository conversations,CourtChatMessageRepository messages,
      CourtRepository courts,AccountRepository accounts,ApplicationEventPublisher events,Clock clock,com.fasterxml.jackson.databind.ObjectMapper json) {
    this.conversations=conversations;this.messages=messages;this.courts=courts;this.accounts=accounts;this.events=events;this.clock=clock;this.json=json;
  }
  Account actor(String email) {
    Account actor=accounts.findByEmail(email).orElseThrow(()->new AccessDeniedException("Vui lòng đăng nhập lại."));
    if (actor.isLocked() || !Set.of("OWNER","CUSTOMER").contains(actor.getRole())) throw new AccessDeniedException("Không có quyền nhắn tin.");
    return actor;
  }
  private void participant(CourtConversation c,Account actor) {
    boolean customer=c.customer.getId().equals(actor.getId()) && "CUSTOMER".equals(actor.getRole());
    boolean owner=c.owner.getId().equals(actor.getId()) && "OWNER".equals(actor.getRole());
    if (!customer&&!owner) throw new AccessDeniedException("Bạn không có quyền truy cập hội thoại này.");
  }
  private CourtConversation load(long id,Account actor,boolean lock) {
    var c=(lock?conversations.lock(id):conversations.findById(id))
        .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy hội thoại."));
    participant(c,actor);return c;
  }
  @Transactional
  public long open(long courtId,String email) {
    var actor=actor(email);
    if (!"CUSTOMER".equals(actor.getRole())) throw new AccessDeniedException("Chỉ tài khoản khách hàng được mở hội thoại với chủ sân.");
    // Serializes concurrent first-open requests; never trust a client-supplied owner ID.
    var court=courts.lockById(courtId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy sân."));
    if (!"OWNER".equals(court.getOwner().getRole())) throw new AccessDeniedException("Sân chưa có chủ sân hợp lệ.");
    var existing=conversations.findByCustomerIdAndCourtId(actor.getId(),courtId);
    if (existing.isPresent()) { participant(existing.get(),actor);return existing.get().id; }
    var c=new CourtConversation();c.court=court;c.customer=actor;c.owner=court.getOwner();c.createdAt=LocalDateTime.now(clock);c.updatedAt=c.createdAt;
    conversations.saveAndFlush(c);return c.id;
  }
  @Transactional
  public long openFromCourt(long courtId,String email){
    var court=courts.findById(courtId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy sân."));
    return open(courtId,email);
  }
  @Transactional(readOnly=true)
  public Owners owners(String email,String query,int page) {
    var actor=actor(email);
    if(!"CUSTOMER".equals(actor.getRole()))throw new AccessDeniedException("Chỉ khách hàng chọn chủ sân.");
    if(query==null||query.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Từ khóa quá dài.");
    var result=accounts.chatOwners(query.trim(),PageRequest.of(Math.max(0,page),20));
    return new Owners(result.stream().map(owner->new OwnerView(owner.getId(),owner.getName(),
        courts.findByOwnerIdOrderByIdDesc(owner.getId()).stream().filter(Court::getActive).limit(5).map(Court::getName).toList())).toList(),result.hasNext());
  }
  @Transactional
  public long openOwner(long ownerId,String email) {
    var customer=actor(email);
    if(!"CUSTOMER".equals(customer.getRole()))throw new AccessDeniedException("Chỉ khách hàng mở hội thoại với chủ sân.");
    var owner=accounts.lockForChat(ownerId).filter(a->"OWNER".equals(a.getRole())&&!a.isLocked())
        .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy chủ sân."));
    if(courts.findByOwnerIdOrderByIdDesc(ownerId).stream().noneMatch(Court::getActive))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Chủ sân chưa có sân đang hoạt động.");
    String key=customer.getId()+":"+ownerId;
    var existing=conversations.findByDirectKey(key);
    if(existing.isPresent())return existing.get().id;
    var c=new CourtConversation();c.customer=customer;c.owner=owner;c.directKey=key;c.createdAt=LocalDateTime.now(clock);c.updatedAt=c.createdAt;
    conversations.saveAndFlush(c);return c.id;
  }
  @Transactional(readOnly=true)
  public Inbox inbox(String email,int page) {
    var actor=actor(email);var result=conversations.inbox(actor.getId(),PageRequest.of(Math.max(0,page),20));
    return new Inbox(result.map(c->summary(c,actor)).getContent(),result.hasNext(),messages.totalUnread(actor.getId()));
  }
  @Transactional(readOnly=true)
  public ThreadView detail(long id,String email) { var actor=actor(email);return summary(load(id,actor,false),actor); }
  private ThreadView summary(CourtConversation c,Account actor) {
    boolean customer=c.customer.getId().equals(actor.getId());var peer=customer?c.owner:c.customer;
    return new ThreadView(c.id,c.court==null?0:c.court.getId(),c.court==null?"Trao đổi với "+c.owner.getName():c.court.getName(),peer.getName(),customer?"OWNER":"CUSTOMER",
        messages.unread(c.id,actor.getId(),customer?c.customerReadThrough:c.ownerReadThrough),stamp(c.updatedAt),c.aiPaused);
  }
  @Transactional(readOnly=true)
  public History history(long id,String email,long before,long after) {
    var actor=actor(email);load(id,actor,false);
    if(before<0||after<0||(before>0&&after>0)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mốc phân trang không hợp lệ.");
    var rows=after>0?messages.newer(id,after,PageRequest.of(0,31)):messages.older(id,before,PageRequest.of(0,31));
    boolean more=rows.size()>30;var batch=new ArrayList<>(rows.subList(0,Math.min(rows.size(),30)));
    batch.sort(Comparator.comparing(m->m.id));return new History(batch.stream().map(this::view).toList(),more);
  }
  @Transactional
  public MessageView send(long id,String email,String clientId,String raw) { return send(id,email,clientId,raw,false); }
  @Transactional
  public MessageView send(long id,String email,String clientId,String raw,boolean askAi) {
    if(raw==null||raw.isBlank()||raw.length()>1000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tin nhắn cần 1–1.000 ký tự.");
    String key;
    try { key=UUID.fromString(clientId).toString(); }
    catch(RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mã gửi tin không hợp lệ."); }
    var actor=actor(email);var c=load(id,actor,true);
    var existing=messages.findByConversationIdAndSenderIdAndClientId(id,actor.getId(),key);
    if(existing.isPresent()) {
      if(!existing.get().content.equals(raw.trim()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Mã gửi tin đã được dùng với nội dung khác.");
      return view(existing.get());
    }
    var m=new CourtChatMessage();m.conversation=c;m.sender=actor;m.clientId=key;m.content=raw.trim();m.sentAt=LocalDateTime.now(clock);
    messages.saveAndFlush(m);c.updatedAt=m.sentAt;
    // Human conversations never invoke Gemini, including legacy askAi requests.
    events.publishEvent(new Changed(c.id,c.customer.getId(),c.owner.getId()));
    return view(m);
  }
  @Transactional
  public void markRead(long id,String email,long through) {
    var actor=actor(email);var c=load(id,actor,true);
    var m=messages.findById(through).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mốc tin nhắn không hợp lệ."));
    if(!m.conversation.id.equals(id))throw new AccessDeniedException("Tin nhắn không thuộc hội thoại.");
    if(c.customer.getId().equals(actor.getId()))c.customerReadThrough=Math.max(c.customerReadThrough,through);
    else c.ownerReadThrough=Math.max(c.ownerReadThrough,through);
    // Only used for the recipient's real unread counter; never exposed as read receipts.
  }
  @Transactional(readOnly=true)
  public long totalUnread(String email) { return messages.totalUnread(actor(email).getId()); }
  @Transactional(readOnly=true)
  public List<AiRequested> abandonedAi(java.time.LocalDateTime before){
    return messages.abandoned(before,PageRequest.of(0,100)).stream().map(m->new AiRequested(m.conversation.id,m.aiReplyTo,m.conversation.customer.getId())).toList();
  }
  @Transactional(readOnly=true)
  public AiContext aiContext(long conversation,long source) {
    var c=conversations.findById(conversation).orElseThrow();
    var pending=messages.findByAiReplyTo(source).filter(m->m.conversation.id==conversation&&"PENDING".equals(m.aiStatus)).orElse(null);
    if(pending==null)return null;
    var sourceMessage=messages.findById(source).orElseThrow();
    var rows=new ArrayList<>(messages.aiContext(conversation,source,c.customer.getId(),PageRequest.of(0,9)));
    rows.sort(Comparator.comparing(m->m.id));
    var history=rows.stream().filter(m->!m.id.equals(source)).map(m->new AiIntentClient.Turn("AI".equals(m.messageKind)?"assistant":"user",m.content)).toList();
    return new AiContext(c.owner.getId(),history,sourceMessage.content);
  }
  @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void finishAi(long conversation,long source,CourtAssistantService.Reply reply) {
    var c=conversations.lock(conversation).orElseThrow();
    var waiting=messages.findByAiReplyTo(source).filter(m->"PENDING".equals(m.aiStatus)).orElse(null);
    if(waiting==null)return; // The owner may have taken over while Gemini was running.
    waiting.aiStatus=reply==null?"FAILED":"DONE";
    waiting.content=reply==null?"AI tạm chưa trả lời được. Tin của bạn đã gửi đến chủ sân và chủ sân vẫn có thể trả lời.":reply.message().substring(0,Math.min(1000,reply.message().length()));
    try{waiting.aiLinks=reply==null?"[]":json.writeValueAsString(reply.links());}catch(Exception ex){waiting.aiLinks="[]";}
    c.updatedAt=LocalDateTime.now(clock);events.publishEvent(new Changed(c.id,c.customer.getId(),c.owner.getId()));
  }
  MessageView view(CourtChatMessage m) {
    boolean ai="AI".equals(m.messageKind);List<CourtAssistantService.Link> links=List.of();
    if(ai&&m.aiLinks!=null)try{links=json.readValue(m.aiLinks,new com.fasterxml.jackson.core.type.TypeReference<List<CourtAssistantService.Link>>(){});}catch(Exception ignored){}
    return new MessageView(m.id,ai?0:m.sender.getId(),ai?"Trợ lý AI":m.sender.getName(),ai?"AI":m.sender.getRole(),m.content,m.clientId,stamp(m.sentAt),m.aiStatus,links);
  }
  private String stamp(LocalDateTime value) { return value.atZone(AppConstants.ZONE).toOffsetDateTime().toString(); }
}
