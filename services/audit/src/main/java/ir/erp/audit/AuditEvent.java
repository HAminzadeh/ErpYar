package ir.erp.audit;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="audit_events")
public class AuditEvent {
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(name="company_id",nullable=false) public UUID companyId;
  @Column(name="actor_id",nullable=false,length=36) public String actorId;
  @Column(nullable=false,length=80) public String action;
  @Column(name="entity_type",nullable=false,length=80) public String entityType;
  @Column(name="entity_id",nullable=false) public UUID entityId;
  @Column(name="occurred_at",nullable=false) public Instant occurredAt=Instant.now();
}
