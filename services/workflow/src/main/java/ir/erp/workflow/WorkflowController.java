package ir.erp.workflow;

import ir.erp.common.Access;
import ir.erp.common.AuditPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/workflows/requests")
public class WorkflowController {
  private final ApprovalRepository repo;private final Access access;private final AuditPublisher audit;
  public WorkflowController(ApprovalRepository repo,Access access,AuditPublisher audit) {
    this.repo=repo;this.access=access;this.audit=audit;
  }
  public record NewApproval(@NotNull UUID companyId,@NotBlank String requestType,@NotBlank String description) {}
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public ApprovalRequest create(@Valid @RequestBody NewApproval req) {
    access.company(req.companyId());
    ApprovalRequest item=new ApprovalRequest();item.companyId=req.companyId();item.requestType=req.requestType();
    item.description=req.description();item.requestedBy=access.actor();
    ApprovalRequest saved=repo.save(item);audit.publish(saved.companyId,"APPROVAL_SUBMITTED","ApprovalRequest",saved.id);return saved;
  }
  @GetMapping public List<ApprovalRequest> list(@RequestParam UUID companyId) {
    access.company(companyId);return repo.findByCompanyIdOrderByCreatedAtDesc(companyId);
  }
  @PostMapping("/{id}/approve") @Transactional
  public ApprovalRequest approve(@PathVariable UUID id) {return decide(id,"APPROVED");}
  @PostMapping("/{id}/reject") @Transactional
  public ApprovalRequest reject(@PathVariable UUID id) {return decide(id,"REJECTED");}
  private ApprovalRequest decide(UUID id,String status) {
    ApprovalRequest item=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Approval not found"));
    access.companyManager(item.companyId);
    if(!item.status.equals("PENDING")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Already decided");
    if(item.requestedBy.equals(access.actor())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Four eyes rule: cannot approve your own request");
    item.status=status;item.decidedBy=access.actor();item.decidedAt=Instant.now();
    ApprovalRequest saved=repo.save(item);audit.publish(item.companyId,"APPROVAL_"+status,"ApprovalRequest",item.id);return saved;
  }
}
