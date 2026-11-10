package vn.uth.court;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.*;

@Service
public class AdminAccountService {
 public record AccountView(long id,String name,String email,String phone,String role,boolean locked,
     boolean google,boolean mustChangePassword,LocalDateTime createdAt) {}
 public record Stats(long customers,long owners,long locked) {}
 private final AccountRepository accounts;
 private final AdminControlRepository control;
 private final AdminAuditRepository audit;
 private final PasswordEncoder passwords;
 public AdminAccountService(AccountRepository accounts,AdminControlRepository control,
     AdminAuditRepository audit,PasswordEncoder passwords){
  this.accounts=accounts;this.control=control;this.audit=audit;this.passwords=passwords;
 }
 private Account admin(String email){
  var a=accounts.findByEmail(email).orElseThrow(()->new AccessDeniedException("Không có quyền quản trị."));
  if(!a.isAdmin()||a.isLocked())throw new AccessDeniedException("Không có quyền quản trị.");return a;
 }
 private void serialize(){control.lockControl().orElseThrow(()->new IllegalStateException("Chưa khởi tạo schema quản trị."));}
 private Account target(long id){return accounts.lockForChat(id).orElseThrow(()->new NotFoundException("Không tìm thấy tài khoản."));}
 private AccountView view(Account a){return new AccountView(a.getId(),a.getName(),a.getEmail(),a.getPhone(),a.getRole(),a.isLocked(),
     a.getGoogleSub()!=null,a.getMustChangePassword(),a.getCreatedAt());}
 @Transactional(readOnly=true) public Stats stats(String actor){admin(actor);return new Stats(accounts.countByRole("CUSTOMER"),accounts.countByRole("OWNER"),accounts.countByLockedTrue());}
 @Transactional(readOnly=true) public Page<AccountView> list(String actor,String q,String role,String state,int page){
  admin(actor);if(q==null||q.length()>100||!Set.of("","ADMIN","OWNER","CUSTOMER").contains(role)||!Set.of("","active","locked").contains(state))
    throw new IllegalArgumentException("Bộ lọc không hợp lệ.");
  return accounts.adminSearch(q.trim(),role,state.isEmpty()?null:state.equals("locked"),PageRequest.of(Math.max(0,page),15)).map(this::view);
 }
 @Transactional(readOnly=true) public AccountView detail(String actor,long id){
  admin(actor);return view(accounts.findById(id).orElseThrow(()->new NotFoundException("Không tìm thấy tài khoản.")));
 }
 @Transactional(readOnly=true) public Page<AdminAudit> journal(String actor,int page){admin(actor);return audit.findAllByOrderByIdDesc(PageRequest.of(Math.max(0,page),20));}
 private void log(Account actor,Account target,String action,String detail){audit.save(new AdminAudit(actor.getId(),target.getId(),action,detail));}
 static String email(String value){
  if(value==null)throw new IllegalArgumentException("Nhập email hợp lệ.");
  String e=value.trim().toLowerCase(Locale.ROOT);
  if(e.length()>190||!e.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))throw new IllegalArgumentException("Nhập email hợp lệ.");return e;
 }
 static String name(String value){
  if(value==null||value.trim().length()<2||value.trim().length()>100)throw new IllegalArgumentException("Tên cần 2–100 ký tự.");
  return value.trim();
 }
 static String phone(String value){return value==null||value.isBlank()?null:Phones.require(value);}
 static void password(String value){
  if(value==null||value.length()<12||value.length()>64||value.getBytes(StandardCharsets.UTF_8).length>72)
    throw new IllegalArgumentException("Mật khẩu khởi tạo cần 12–64 ký tự và tối đa 72 byte UTF-8.");
 }
 private void unique(String email,String phone,Long except){
  accounts.findByEmailIgnoreCase(email).filter(a->!Objects.equals(a.getId(),except)).ifPresent(a->{throw new IllegalArgumentException("Email đã được sử dụng.");});
  if(phone!=null)accounts.findByPhone(phone).filter(a->!Objects.equals(a.getId(),except)).ifPresent(a->{throw new IllegalArgumentException("Số điện thoại đã được sử dụng.");});
 }
 @Transactional public void update(String actor,long id,String name,String email,String phone){
  serialize();var by=admin(actor);var a=target(id);String e=email(email),p=phone(phone);
  if(a.getGoogleSub()!=null&&!e.equals(a.getEmail()))throw new IllegalArgumentException("Không đổi email tài khoản đã liên kết Google.");
  unique(e,p,a.getId());a.setName(name(name));a.setEmail(e);a.setPhone(p);a.revokeSessions();
  log(by,a,"PROFILE_UPDATED","fields=name,email,phone; sessions revoked");
 }
 private void protect(Account by,Account a,boolean locking){
  if(by.getId().equals(a.getId())&&locking)throw new IllegalArgumentException("Không được tự khóa tài khoản quản trị.");
  if(a.isAdmin()&&!a.isLocked()&&locking&&accounts.countByRoleAndLockedFalse("ADMIN")<=1)
    throw new IllegalArgumentException("Phải giữ ít nhất một quản trị viên đang hoạt động.");
 }
 @Transactional public void lock(String actor,long id,boolean locked){
  serialize();var by=admin(actor);var a=target(id);protect(by,a,locked);
  if(a.isLocked()==locked)return;
  a.setLocked(locked);a.revokeSessions();log(by,a,locked?"ACCOUNT_LOCKED":"ACCOUNT_UNLOCKED","sessions revoked");
 }
 @Transactional public long bootstrap(String name,String email,String phone,String password){
  serialize();if(accounts.countByRole("ADMIN")!=0)throw new IllegalArgumentException("Đã có quản trị viên; không khởi tạo lại.");
  String e=email(email),p=phone(phone);password(password);unique(e,p,null);
  var a=new Account();a.setName(name(name));a.setEmail(e);a.setPhone(p);a.setRole("ADMIN");a.setPassword(passwords.encode(password));
  accounts.saveAndFlush(a);log(a,a,"ADMIN_BOOTSTRAPPED","first administrator");return a.getId();
 }
}
