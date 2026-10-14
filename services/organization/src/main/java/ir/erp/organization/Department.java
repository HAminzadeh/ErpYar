package ir.erp.organization;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="departments",uniqueConstraints=@UniqueConstraint(columnNames={"company_id","code"}))
public class Department {
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(name="company_id",nullable=false) public UUID companyId;
  @Column(name="parent_id") public UUID parentId;
  @Column(nullable=false,length=40) public String code;
  @Column(nullable=false,length=160) public String name;
}
