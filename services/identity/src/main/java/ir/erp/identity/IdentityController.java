package ir.erp.identity;

import ir.erp.common.Access;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class IdentityController {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtEncoder jwt;
  private final Access access;
  public IdentityController(UserRepository users,PasswordEncoder encoder,JwtEncoder jwt,Access access) {
    this.users=users;this.encoder=encoder;this.jwt=jwt;this.access=access;
  }
  public record LoginRequest(@NotBlank String username,@NotBlank String password) {}
  public record LoginResponse(String accessToken,String tokenType,int expiresIn) {}
  public record NewUser(@NotBlank String username,@NotBlank @Size(min=12) String password,@NotBlank String displayName) {}
  public record AccessUpdate(Set<String> roles,Set<UUID> holdingIds,Set<UUID> companyIds,Boolean active) {}
  public record UserView(UUID id,String username,String displayName,boolean active,Set<String> roles,Set<String> holdingIds,Set<String> companyIds) {}
  private UserView view(AppUser u) {return new UserView(u.id,u.username,u.displayName,u.active,u.roles,u.holdingIds,u.companyIds);}
  @PostMapping("/api/auth/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    AppUser u=users.findByUsernameIgnoreCase(request.username().trim()).orElse(null);
    if(u==null || !u.active || !encoder.matches(request.password(),u.passwordHash))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
    Instant now=Instant.now();
    JwtClaimsSet claims=JwtClaimsSet.builder().issuer("erp-local").subject(u.id.toString())
      .issuedAt(now).expiresAt(now.plus(15,ChronoUnit.MINUTES))
      .claim("username",u.username).claim("roles",List.copyOf(u.roles))
      .claim("holding_ids",List.copyOf(u.holdingIds)).claim("company_ids",List.copyOf(u.companyIds)).build();
    String token=jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    return new LoginResponse(token,"Bearer",900);
  }
  @GetMapping("/api/auth/me")
  public UserView me() {return view(users.findById(UUID.fromString(access.actor()))
    .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found")));}
  @GetMapping("/api/users") @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public List<UserView> all() {return users.findAll().stream().map(this::view).toList();}
  @PostMapping("/api/users") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public UserView create(@Valid @RequestBody NewUser request) {
    String username=request.username().trim().toLowerCase();
    if(users.findByUsernameIgnoreCase(username).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Username exists");
    AppUser u=new AppUser();u.username=username;u.displayName=request.displayName().trim();
    u.passwordHash=encoder.encode(request.password());u.roles=new java.util.HashSet<>(Set.of("STAFF"));
    return view(users.save(u));
  }
  @PutMapping("/api/users/{id}/access") @PreAuthorize("hasRole('SYSTEM_ADMIN')")
  public UserView updateAccess(@PathVariable UUID id,@Valid @RequestBody AccessUpdate request) {
    AppUser u=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found"));
    Set<String> valid=Set.of("SYSTEM_ADMIN","HOLDING_ADMIN","COMPANY_ADMIN","STAFF");
    if(request.roles()!=null) {
      if(request.roles().isEmpty()||!valid.containsAll(request.roles())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid roles");
      if(u.id.toString().equals(access.actor()) && !request.roles().contains("SYSTEM_ADMIN"))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cannot remove own administrator role");
      u.roles=new java.util.HashSet<>(request.roles());
    }
    if(request.holdingIds()!=null) u.holdingIds=new java.util.HashSet<>(request.holdingIds().stream().map(UUID::toString).toList());
    if(request.companyIds()!=null) u.companyIds=new java.util.HashSet<>(request.companyIds().stream().map(UUID::toString).toList());
    if(request.active()!=null) {
      if(u.id.toString().equals(access.actor()) && !request.active()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cannot deactivate yourself");
      u.active=request.active();
    }
    return view(users.save(u));
  }
}
