package ir.erp.master;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="categories",uniqueConstraints=@UniqueConstraint(columnNames={"company_id","code"}))
public class Category {
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(name="company_id",nullable=false) public UUID companyId;
  @Column(nullable=false,length=40) public String code;
  @Column(nullable=false,length=160) public String name;
  @Column(nullable=false) public boolean active=true;
}
