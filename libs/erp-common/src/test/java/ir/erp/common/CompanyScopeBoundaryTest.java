package ir.erp.common;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Offline regression checks for the existing company-scoped authorization helper. */
class CompanyScopeBoundaryTest {
  private final Access access = new Access();

  private void login(String role, UUID holding, UUID company) {
    var claims = Map.<String, Object>of(
        "sub", UUID.randomUUID().toString(),
        "roles", List.of(role),
        "holding_ids", List.of(holding.toString()),
        "company_ids", List.of(company.toString()));
    var token = new Jwt("test-token", Instant.now(), Instant.now().plusSeconds(60),
        Map.of("alg", "HS256"), claims);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        token, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
  }

  @AfterEach void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test void staffCannotManageEvenGrantedCompany() {
    UUID company = UUID.randomUUID();
    login("STAFF", UUID.randomUUID(), company);
    assertTrue(access.canCompany(company));
    assertThrows(AccessDeniedException.class, () -> access.companyManager(company));
  }

  @Test void companyAdminCannotManageAnotherCompany() {
    UUID company = UUID.randomUUID();
    login("COMPANY_ADMIN", UUID.randomUUID(), company);
    assertDoesNotThrow(() -> access.companyManager(company));
    assertThrows(AccessDeniedException.class,
        () -> access.companyManager(UUID.randomUUID()));
  }

  @Test void holdingAdminGrantDoesNotImplyCompanyGrant() {
    UUID holding = UUID.randomUUID();
    login("HOLDING_ADMIN", holding, UUID.randomUUID());
    assertTrue(access.canHolding(holding));
    assertFalse(access.canHolding(UUID.randomUUID()));
    assertThrows(AccessDeniedException.class,
        () -> access.company(UUID.randomUUID()));
  }

  @Test void staffCannotReadUngrantableHolding() {
    UUID holding = UUID.randomUUID();
    login("STAFF", holding, UUID.randomUUID());
    assertFalse(access.canHolding(holding));
    assertThrows(AccessDeniedException.class, () -> access.holding(holding));
  }
}
