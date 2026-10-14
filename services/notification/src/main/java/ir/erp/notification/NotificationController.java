package ir.erp.notification;

import ir.erp.common.Access;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationRepository repo;private final Access access;
  public NotificationController(NotificationRepository repo,Access access) {this.repo=repo;this.access=access;}
  public record NewNotification(@NotNull UUID companyId,@NotNull UUID recipientId,@NotBlank String title,@NotBlank String body) {}
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public InAppNotification create(@Valid @RequestBody NewNotification req) {
    access.companyManager(req.companyId());
    InAppNotification n=new InAppNotification();n.companyId=req.companyId();n.recipientId=req.recipientId().toString();
    n.title=req.title();n.body=req.body();return repo.save(n);
  }
  @GetMapping("/mine")
  public List<InAppNotification> mine(@RequestParam UUID companyId) {
    access.company(companyId);return repo.findByCompanyIdAndRecipientIdOrderByCreatedAtDesc(companyId,access.actor());
  }
  @PostMapping("/{id}/read")
  public InAppNotification markRead(@PathVariable UUID id) {
    InAppNotification n=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found"));
    access.company(n.companyId);
    if(!n.recipientId.equals(access.actor())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Recipient mismatch");
    if(n.readAt==null) {n.readAt=Instant.now();return repo.save(n);}return n;
  }
}
