package ir.erp.common;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class AccessTest {
  private void principal(String role,String company) {
    Jwt j=new Jwt("token",Instant.now(),Instant.now().plusSeconds(60),Map.of("alg","HS256"),
      Map.of("sub",UUID.randomUUID().toString(),"company_ids",List.of(company),"holding_ids",List.of(),"roles",List.of(role)));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(j,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
  }
  @Test void companyIdMustBeInMembership() {
    UUID allowed=UUID.randomUUID();principal("STAFF",allowed.toString());
    Access access=new Access();assertTrue(access.canCompany(allowed));assertFalse(access.canCompany(UUID.randomUUID()));
    SecurityContextHolder.clearContext();
  }
  @Test void systemAdminIsNotTenantBound() {
    principal("SYSTEM_ADMIN",UUID.randomUUID().toString());
    assertTrue(new Access().canCompany(UUID.randomUUID()));SecurityContextHolder.clearContext();
  }
}
