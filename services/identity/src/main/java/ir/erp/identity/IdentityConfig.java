package ir.erp.identity;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class IdentityConfig {
  private static final Logger log=LoggerFactory.getLogger(IdentityConfig.class);
  @Bean PasswordEncoder passwordEncoder() {return new BCryptPasswordEncoder(12);}
  @Bean JwtEncoder jwtEncoder(@Value("${erp.jwt.secret}") String secret) {
    if(secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalStateException("Weak JWT_SECRET");
    return new NimbusJwtEncoder(new ImmutableSecret<>(secret.getBytes(StandardCharsets.UTF_8)));
  }
  @Bean ApplicationRunner adminBootstrap(UserRepository users,PasswordEncoder encoder,
       @Value("${BOOTSTRAP_ADMIN_USERNAME:admin}") String name,
       @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
    return args -> {
      if(users.count()==0) {
        if(password.length()<16) throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD needs >= 16 characters");
        AppUser admin=new AppUser();admin.username=name.toLowerCase();admin.displayName="Bootstrap Administrator";
        admin.passwordHash=encoder.encode(password); admin.roles=new java.util.HashSet<>(Set.of("SYSTEM_ADMIN"));
        users.save(admin);log.info("Initial admin user created: {} (password omitted)",name);
      }
    };
  }
}
