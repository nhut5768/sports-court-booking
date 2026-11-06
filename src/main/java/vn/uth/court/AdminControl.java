package vn.uth.court;
import jakarta.persistence.*;
@Entity @Table(name="admin_control")
public class AdminControl {
 @Id private Long id;
 protected AdminControl() {}
 public AdminControl(long id){this.id=id;}
}
