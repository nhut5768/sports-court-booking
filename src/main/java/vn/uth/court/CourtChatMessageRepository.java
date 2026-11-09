package vn.uth.court;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CourtChatMessageRepository extends JpaRepository<CourtChatMessage,Long> {
  @Query("select m from CourtChatMessage m where m.aiStatus='PENDING' and m.sentAt<:before order by m.id")
  List<CourtChatMessage> abandoned(@Param("before") java.time.LocalDateTime before,Pageable page);
  Optional<CourtChatMessage> findByAiReplyTo(Long source);
  java.util.List<CourtChatMessage> findByConversationIdAndAiStatus(Long conversation,String aiStatus);
  @Query("select m from CourtChatMessage m left join m.sender s where m.conversation.id=:conversation and m.id<=:source and (s.id=:customer or (m.messageKind='AI' and m.aiStatus='DONE')) order by m.id desc")
  List<CourtChatMessage> aiContext(@Param("conversation") Long conversation,@Param("source") long source,@Param("customer") long customer,Pageable page);
  Optional<CourtChatMessage> findByConversationIdAndSenderIdAndClientId(Long conversation,Long sender,String client);
  @Query("select m from CourtChatMessage m where m.conversation.id=:conversation and (:before=0 or m.id<:before) order by m.id desc")
  List<CourtChatMessage> older(@Param("conversation") Long conversation,@Param("before") long before,Pageable page);
  @Query("select m from CourtChatMessage m where m.conversation.id=:conversation and m.id>:after order by m.id")
  List<CourtChatMessage> newer(@Param("conversation") Long conversation,@Param("after") long after,Pageable page);
  @Query("select count(m) from CourtChatMessage m where m.conversation.id=:conversation and m.sender.id<>:actor and m.id>:through")
  long unread(@Param("conversation") Long conversation,@Param("actor") Long actor,@Param("through") long through);
  @Query("select count(m) from CourtChatMessage m join m.conversation c where m.sender.id<>:actor and ((c.customer.id=:actor and m.id>c.customerReadThrough) or (c.owner.id=:actor and m.id>c.ownerReadThrough))")
  long totalUnread(@Param("actor") Long actor);
}
