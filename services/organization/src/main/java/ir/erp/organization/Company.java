package ir.erp.organization;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="companies")
public class Company {
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(name="holding_id",nullable=false) public UUID holdingId;
  @Column(nullable=false,unique=true,length=40) public String code;
  @Column(nullable=false,length=200) public String name;
  @Column(name="business_type",nullable=false,length=30) public String businessType;
  @Column(nullable=false) public boolean active=true;
}
