package ir.erp.workflow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApprovalRepository extends JpaRepository<ApprovalRequest,UUID> {List<ApprovalRequest> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);}
