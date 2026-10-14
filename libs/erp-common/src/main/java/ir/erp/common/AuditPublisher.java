package ir.erp.common;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Development-only best-effort HTTP publisher. Replace with transactional outbox before production. */
@Component
public class AuditPublisher {
  private static final Logger log=LoggerFactory.getLogger(AuditPublisher.class);
  private final RestClient client;
  private final Access access;
  public AuditPublisher(@Value("${erp.audit.url:http://audit:8080}") String url, Access access) {
    this.client=RestClient.builder().baseUrl(url).build(); this.access=access;
  }
  public void publish(UUID companyId, String action, String entityType, UUID entityId) {
    try {
      client.post().uri("/api/audit/events")
        .header(HttpHeaders.AUTHORIZATION,"Bearer "+access.token())
        .body(Map.of("companyId",companyId.toString(),"action",action,"entityType",entityType,"entityId",entityId.toString()))
        .retrieve().toBodilessEntity();
    } catch (Exception ex) {log.warn("Audit delivery failed for {}: {}",action,ex.getMessage());}
  }
}
