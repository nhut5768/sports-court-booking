package vn.uth.court;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class WebErrors {
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String badRequest(IllegalArgumentException e, Model m) {
    m.addAttribute("message", e.getMessage());
    return "error";
  }

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String missing(NotFoundException e, Model m) {
    m.addAttribute("message", e.getMessage());
    return "error";
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public String denied(Model m) {
    m.addAttribute("message", "Bạn không có quyền thực hiện thao tác này.");
    return "error";
  }
}
