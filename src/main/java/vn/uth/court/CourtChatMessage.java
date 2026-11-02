package vn.uth.court;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="court_chat_messages", uniqueConstraints=@UniqueConstraint(name="uk_chat_retry",columnNames={"conversation_id","sender_id","client_id"}))
public class CourtChatMessage {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  @ManyToOne(optional=false) @JoinColumn(name="conversation_id") CourtConversation conversation;
  @ManyToOne @JoinColumn(name="sender_id") Account sender;
  @Column(nullable=false,length=36) String clientId;
  @Column(nullable=false,length=1000) String content;
  @Column(nullable=false) LocalDateTime sentAt;
  @Column(nullable=false,length=16) String messageKind="HUMAN";
  @Column(nullable=false,length=16) String aiStatus="NONE";
  @Column(unique=true) Long aiReplyTo;
  @Column(columnDefinition="TEXT") String aiLinks;
}
