package ir.erp.notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationRepository extends JpaRepository<InAppNotification,UUID> {
  List<InAppNotification> findByCompanyIdAndRecipientIdOrderByCreatedAtDesc(UUID companyId,String recipientId);
}
