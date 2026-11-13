package vn.uth.court;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes=CourtMessageApi.class) @Order(-10)
public class CourtMessageErrors {
  @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> denied(){return error(403,"Bạn không có quyền truy cập hội thoại này.");}
  @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> invalid(){return error(400,"Tin nhắn cần 1–1.000 ký tự và mã gửi hợp lệ.");}
  @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException ex){return error(ex.getStatusCode().value(),ex.getReason());}
  private ResponseEntity<?> error(int status,String message){
    var response=ResponseEntity.status(status).cacheControl(CacheControl.noStore());
    if(status==429)response.header("Retry-After","60");
    return response.body(Map.of("message",message==null?"Không thực hiện được yêu cầu.":message));
  }
}
