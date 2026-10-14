package ir.erp.organization;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="holdings")
public class Holding {
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(nullable=false,unique=true,length=40) public String code;
  @Column(nullable=false,length=200) public String name;
  @Column(nullable=false) public boolean active=true;
}
