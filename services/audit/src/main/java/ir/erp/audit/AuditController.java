package ir.erp.audit;

import ir.erp.common.Access;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/audit/events")
public class AuditController {
  private final AuditRepository repo;private final Access access;
  public AuditController(AuditRepository repo,Access access) {this.repo=repo;this.access=access;}
  public record NewEvent(@NotNull UUID companyId,@NotBlank String action,@NotBlank String entityType,@NotNull UUID entityId) {}
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public AuditEvent append(@Valid @RequestBody NewEvent req) {
    access.company(req.companyId());
    AuditEvent event=new AuditEvent();event.companyId=req.companyId();event.action=req.action();
    event.actorId=access.actor();event.entityType=req.entityType();event.entityId=req.entityId();return repo.save(event);
  }
  @GetMapping
  public List<AuditEvent> list(@RequestParam UUID companyId) {
    access.companyManager(companyId);return repo.findByCompanyIdOrderByOccurredAtDesc(companyId);
  }
}
