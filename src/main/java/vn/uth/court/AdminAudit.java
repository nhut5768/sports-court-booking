package vn.uth.court;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="admin_audit")
public class AdminAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long actorId;
 @Column(nullable=false) private Long targetId;
 @Column(nullable=false,length=40) private String action;
 @Column(nullable=false,length=250) private String detail;
 @Column(nullable=false) private LocalDateTime createdAt;
 protected AdminAudit(){}
 public AdminAudit(long actor,long target,String action,String detail){
  this.actorId=actor;this.targetId=target;this.action=action;this.detail=detail;this.createdAt=LocalDateTime.now(AppConstants.ZONE);
 }
 public Long getId(){return id;} public Long getActorId(){return actorId;} public Long getTargetId(){return targetId;}
 public String getAction(){return action;} public String getDetail(){return detail;}
 public LocalDateTime getCreatedAt(){return createdAt;}
}
