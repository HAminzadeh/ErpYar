package ir.erp.common;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String,Object>> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",String.valueOf(e.getReason()),"timestamp",Instant.now().toString()));
  }
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String,Object>> denied(AccessDeniedException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error","Access denied","timestamp",Instant.now().toString()));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String,Object>> invalid(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest().body(Map.of("error","Invalid request", "details",e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).toList()));
  }
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String,String>> conflict(DataIntegrityViolationException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Duplicate value or integrity constraint failed"));
  }
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<Map<String,String>> collision(ObjectOptimisticLockingFailureException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Record was updated concurrently"));
  }
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String,String>> bad(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()==null?"Invalid argument":e.getMessage()));
  }
}
