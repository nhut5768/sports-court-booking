package vn.uth.court;

import jakarta.validation.constraints.*;

public class RegistrationForm {
  @NotBlank(message = "Vui lòng nhập họ tên")
  @Size(max = 100)
  public String name;

  @NotBlank(message = "Vui lòng nhập email")
  @Email(message = "Email không hợp lệ")
  @Size(max = 190)
  public String email;

  @NotBlank(message = "Vui lòng nhập số điện thoại")
  @Pattern(
      regexp = "\\s*(\\+84|84|0)[\\s.-]*[35789]([\\s.-]*\\d){8}\\s*",
      message = Phones.RULE)
  public String phone;

  @NotBlank
  @Size(min = 8, max = 64, message = "Mật khẩu cần 8–64 ký tự")
  public String password;

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }

  public String getPassword() {
    return password;
  }

  public void setName(String x) {
    name = x;
  }

  public void setEmail(String x) {
    email = x;
  }

  public void setPhone(String x) {
    phone = x;
  }

  public void setPassword(String x) {
    password = x;
  }
}
