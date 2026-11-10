package vn.uth.court;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.DataIntegrityViolationException;

@Controller @RequestMapping("/admin")
public class AdminController {
 private final AdminAccountService service;
 public AdminController(AdminAccountService service){this.service=service;}
 @GetMapping({"","/accounts"})
 String index(Principal p,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String role,
   @RequestParam(defaultValue="")String state,@RequestParam(defaultValue="0")int page,Model m){
  m.addAttribute("stats",service.stats(p.getName()));
  m.addAttribute("accounts",service.list(p.getName(),q,role,state,page));
  m.addAttribute("q",q);m.addAttribute("role",role);m.addAttribute("state",state);
  return "admin-accounts";
 }
 @GetMapping("/accounts/{id:[0-9]+}")
 String detail(Principal p,@PathVariable long id,Model m){
  m.addAttribute("entry",service.detail(p.getName(),id));return "admin-account";
 }
 @GetMapping("/audit")String audit(Principal p,@RequestParam(defaultValue="0")int page,Model m){
  m.addAttribute("journal",service.journal(p.getName(),page));
  m.addAttribute("actionLabels",java.util.Map.of("ADMIN_BOOTSTRAPPED","Khởi tạo admin","OWNER_CREATED","Tạo chủ sân",
      "PROFILE_UPDATED","Cập nhật hồ sơ","ACCOUNT_LOCKED","Khóa tài khoản","ACCOUNT_UNLOCKED","Mở khóa","ROLE_CHANGED","Đổi vai trò"));
  return "admin-audit";
 }
 private String act(Runnable action,String redirect,RedirectAttributes r){
  try{action.run();r.addFlashAttribute("success","Đã lưu thay đổi.");}
  catch(IllegalArgumentException e){r.addFlashAttribute("error",e.getMessage());}
  catch(DataIntegrityViolationException e){r.addFlashAttribute("error","Thông tin trùng hoặc dữ liệu đã thay đổi. Vui lòng kiểm tra lại.");}
  return "redirect:"+redirect;
 }
 @PostMapping("/accounts/{id}/profile")
 String update(Principal p,@PathVariable long id,@RequestParam String name,@RequestParam String email,
   @RequestParam(defaultValue="")String phone,RedirectAttributes r){
  return act(()->service.update(p.getName(),id,name,email,phone),"/admin/accounts/"+id,r);
 }
 @PostMapping("/accounts/{id}/lock")
 String lock(Principal p,@PathVariable long id,@RequestParam boolean locked,RedirectAttributes r){
  return act(()->service.lock(p.getName(),id,locked),"/admin/accounts/"+id,r);
 }
}
