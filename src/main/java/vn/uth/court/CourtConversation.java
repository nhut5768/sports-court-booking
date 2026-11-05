package vn.uth.court;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="court_conversations", uniqueConstraints=@UniqueConstraint(name="uk_chat_customer_court",columnNames={"customer_id","court_id"}))
public class CourtConversation {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  @ManyToOne @JoinColumn(name="court_id") Court court;
  @ManyToOne(optional=false) @JoinColumn(name="customer_id") Account customer;
  @ManyToOne(optional=false) @JoinColumn(name="owner_id") Account owner;
  @Column(nullable=false) LocalDateTime createdAt;
  @Column(nullable=false) LocalDateTime updatedAt;
  @Column(nullable=false) long customerReadThrough;
  @Column(nullable=false) long ownerReadThrough;
  @Column(unique=true,length=80) String directKey;
  @Column(nullable=false) boolean aiPaused;
}
