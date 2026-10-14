package ir.erp.master;

import ir.erp.common.Access;
import ir.erp.common.AuditPublisher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/master-data/categories")
public class CategoryController {
  private final CategoryRepository repo;
  private final Access access;
  private final AuditPublisher audit;
  public CategoryController(CategoryRepository repo,Access access,AuditPublisher audit) {this.repo=repo;this.access=access;this.audit=audit;}
  public record NewCategory(@NotNull UUID companyId,@NotBlank String code,@NotBlank String name) {}
  @GetMapping public List<Category> list(@RequestParam UUID companyId) {
    access.company(companyId);return repo.findByCompanyId(companyId);
  }
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  public Category create(@Valid @RequestBody NewCategory req) {
    access.companyManager(req.companyId());
    Category c=new Category();c.companyId=req.companyId();c.code=req.code().trim();c.name=req.name().trim();
    Category saved=repo.save(c);audit.publish(c.companyId,"CATEGORY_CREATED","Category",saved.id);return saved;
  }
}
