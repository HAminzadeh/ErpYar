package ir.erp.workflow;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="approval_requests")
public class ApprovalRequest {
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(name="company_id",nullable=false) public UUID companyId;
  @Column(name="request_type",nullable=false,length=60) public String requestType;
  @Column(nullable=false,length=1000) public String description;
  @Column(name="requested_by",nullable=false,length=36) public String requestedBy;
  @Column(nullable=false,length=20) public String status="PENDING";
  @Column(name="decided_by",length=36) public String decidedBy;
  @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
  @Column(name="decided_at") public Instant decidedAt;
  @Version public long version;
}
