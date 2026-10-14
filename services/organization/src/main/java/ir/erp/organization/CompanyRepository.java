package ir.erp.organization;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CompanyRepository extends JpaRepository<Company,UUID> {List<Company> findByHoldingId(UUID holdingId);}
