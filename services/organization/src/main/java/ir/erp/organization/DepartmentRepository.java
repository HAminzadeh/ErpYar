package ir.erp.organization;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DepartmentRepository extends JpaRepository<Department,UUID> {List<Department> findByCompanyId(UUID companyId);}
