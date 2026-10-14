package ir.erp.notification;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="in_app_notifications")
public class InAppNotification {
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(name="company_id",nullable=false) public UUID companyId;
  @Column(name="recipient_id",nullable=false,length=36) public String recipientId;
  @Column(nullable=false,length=160) public String title;
  @Column(nullable=false,length=2000) public String body;
  @Column(name="read_at") public Instant readAt;
  @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
}
