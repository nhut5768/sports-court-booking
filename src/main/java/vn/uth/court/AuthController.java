package vn.uth.court;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
  private final AccountService accounts;
  private final org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> clients;

  public AuthController(AccountService accounts, org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> clients) {
    this.accounts = accounts;
    this.clients = clients;
  }

  @GetMapping("/login")
  String login(Model model) {
    model.addAttribute("googleEnabled", clients.getIfAvailable() != null);
    return "login";
  }

  @GetMapping("/account/password")
  String changePassword(Principal p, Model m) {
    m.addAttribute("forced", accounts.current(p.getName()).getMustChangePassword());
    return "change-password";
  }

  @PostMapping("/account/password")
  String changePasswordPost(
      @RequestParam String current,
      @RequestParam String password,
      @RequestParam String confirm,
      Principal p,
      RedirectAttributes r) {
    try {
      accounts.changePassword(p.getName(), current, password, confirm);
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
      return "redirect:/account/password";
    }
    r.addFlashAttribute("success", "Đã đổi mật khẩu.");
    return "redirect:/account";
  }

  @PostMapping("/account/phone")
  String savePhone(@RequestParam String phone, Principal p, RedirectAttributes r) {
    try {
      accounts.updatePhone(p.getName(), phone);
      r.addFlashAttribute("success", "Đã cập nhật số điện thoại.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      r.addFlashAttribute("error", "Số điện thoại đã được dùng cho tài khoản khác.");
    }
    return "redirect:/account";
  }

  @GetMapping({"/register","/register/customer"})
  String register(Model m) {
    m.addAttribute("form", new RegistrationForm());
    return "register";
  }

  @PostMapping({"/register","/register/customer"})
  String registerPost(@Valid @ModelAttribute("form") RegistrationForm f, BindingResult r) {
    if (r.hasErrors()) {
      return "register";
    }
    try {
      accounts.register(f);
    } catch (IllegalArgumentException e) {
      r.reject("register", e.getMessage());
      return "register";
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      r.reject("register", "Email hoặc số điện thoại đã được đăng ký.");
      return "register";
    }
    return "redirect:/login?registered";
  }

  @GetMapping("/register/owner")
  String ownerRegister(Model m) {
    m.addAttribute("form",new RegistrationForm());return "register-owner";
  }
  @PostMapping("/register/owner")
  String ownerRegisterPost(@Valid @ModelAttribute("form") RegistrationForm f,BindingResult r) {
    if(r.hasErrors())return "register-owner";
    try { accounts.registerOwner(f); }
    catch(IllegalArgumentException e){r.reject("register",e.getMessage());return "register-owner";}
    catch(org.springframework.dao.DataIntegrityViolationException e){r.reject("register","Email hoặc số điện thoại đã được đăng ký.");return "register-owner";}
    return "redirect:/login?owner_registered";
  }

  @GetMapping("/account")
  String account(Principal p, Model m) {
    m.addAttribute("account", accounts.current(p.getName()));
    m.addAttribute("banks", AppConstants.BANKS);
    return "account";
  }

  @PostMapping("/account/bank")
  String saveBank(
      @RequestParam String bankCode,
      @RequestParam String bankAccountNo,
      @RequestParam String bankAccountName,
      Principal p,
      RedirectAttributes r) {
    try {
      accounts.updateBank(p.getName(), bankCode, bankAccountNo, bankAccountName);
      r.addFlashAttribute("success", "Đã lưu tài khoản nhận cọc.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/account#bank";
  }

  @PostMapping("/account/payment-qr")
  String savePaymentQr(
      @RequestParam("qr") MultipartFile qr, Principal p, RedirectAttributes r) {
    try {
      accounts.updatePaymentQr(p.getName(), qr);
      r.addFlashAttribute("success", "Đã lưu ảnh QR nhận cọc.");
    } catch (IllegalArgumentException e) {
      r.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/account#bank";
  }

  @PostMapping("/account/payment-qr/delete")
  String deletePaymentQr(Principal p, RedirectAttributes r) {
    accounts.removePaymentQr(p.getName());
    r.addFlashAttribute("success", "Đã xóa ảnh QR nhận cọc.");
    return "redirect:/account#bank";
  }
}
