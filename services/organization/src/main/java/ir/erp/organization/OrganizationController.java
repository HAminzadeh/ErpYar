package ir.erp.organization;

import ir.erp.common.Access;
import ir.erp.common.AuditPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrganizationController {
  private final HoldingRepository holdings;
  private final CompanyRepository companies;
  private final DepartmentRepository departments;
  private final Access access; private final AuditPublisher audit;
  public OrganizationController(HoldingRepository h,CompanyRepository c,DepartmentRepository d,Access a,AuditPublisher audit) {
    this.holdings=h;this.companies=c;this.departments=d;this.access=a;this.audit=audit;
  }
  public record HoldingRequest(@NotBlank String code,@NotBlank String name) {}
  public record CompanyRequest(@NotNull UUID holdingId,@NotBlank String code,@NotBlank String name,@NotBlank String businessType) {}
  public record DepartmentRequest(@NotNull UUID companyId,UUID parentId,@NotBlank String code,@NotBlank String name) {}
  @GetMapping("/api/holdings")
  public List<Holding> holdings() {return holdings.findAll().stream().filter(h->access.canHolding(h.id)).toList();}
  @PostMapping("/api/holdings") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public Holding createHolding(@Valid @RequestBody HoldingRequest req) {
    Holding h=new Holding();h.code=req.code().trim();h.name=req.name().trim();return holdings.save(h);
  }
  @GetMapping("/api/companies")
  public List<Company> companies(@RequestParam(required=false) UUID holdingId) {
    return companies.findAll().stream().filter(c -> (holdingId==null || holdingId.equals(c.holdingId)) &&
      (access.canCompany(c.id) || access.canHolding(c.holdingId))).toList();
  }
  @PostMapping("/api/companies") @ResponseStatus(HttpStatus.CREATED)
  public Company createCompany(@Valid @RequestBody CompanyRequest req) {
    access.holding(req.holdingId());
    if(!holdings.existsById(req.holdingId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Holding not found");
    if(!Set.of("SERVICE","MANUFACTURING","INVESTMENT","TRADE","OTHER").contains(req.businessType()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown businessType");
    Company c=new Company();c.holdingId=req.holdingId();c.code=req.code().trim();c.name=req.name().trim();c.businessType=req.businessType();
    return companies.save(c);
  }
  private Company company(UUID companyId) {
    return companies.findById(companyId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Company not found"));
  }
  private void canManage(Company c) {
    if(access.role("SYSTEM_ADMIN") || (access.role("COMPANY_ADMIN")&&access.canCompany(c.id)) || access.canHolding(c.holdingId)) return;
    throw new AccessDeniedException("Company management denied");
  }
  @GetMapping("/api/departments")
  public List<Department> departments(@RequestParam UUID companyId) {
    Company c=company(companyId);
    if(!(access.canCompany(companyId)||access.canHolding(c.holdingId))) throw new AccessDeniedException("Company access denied");
    return departments.findByCompanyId(companyId);
  }
  @PostMapping("/api/departments") @ResponseStatus(HttpStatus.CREATED)
  public Department createDepartment(@Valid @RequestBody DepartmentRequest req) {
    Company c=company(req.companyId());canManage(c);
    if(req.parentId()!=null) {
      Department parent=departments.findById(req.parentId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Parent department not found"));
      if(!parent.companyId.equals(c.id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Parent must be in same company");
    }
    Department d=new Department();d.companyId=req.companyId();d.parentId=req.parentId();d.code=req.code().trim();d.name=req.name().trim();
    Department saved=departments.save(d);audit.publish(c.id,"DEPARTMENT_CREATED","Department",saved.id);return saved;
  }
}
