package ir.erp.common;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class Access {
  public JwtAuthenticationToken principal() {
    return (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
  }
  public String actor() { return principal().getToken().getSubject(); }
  public String token() { return principal().getToken().getTokenValue(); }
  public boolean role(String value) {return principal().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_"+value));}
  private List<String> list(String claim) {
    List<String> values=principal().getToken().getClaimAsStringList(claim);
    return values==null ? List.of() : values;
  }
  public boolean canHolding(UUID id) {
    return role("SYSTEM_ADMIN") || (role("HOLDING_ADMIN") && list("holding_ids").contains(id.toString()));
  }
  public boolean canCompany(UUID id) {
    return role("SYSTEM_ADMIN") || list("company_ids").contains(id.toString());
  }
  public void holding(UUID id) { if(!canHolding(id)) throw new AccessDeniedException("Holding access denied"); }
  public void company(UUID id) { if(!canCompany(id)) throw new AccessDeniedException("Company access denied"); }
  public void companyManager(UUID id) {
    company(id);
    if(!(role("SYSTEM_ADMIN")||role("COMPANY_ADMIN"))) throw new AccessDeniedException("Company admin required");
  }
}
