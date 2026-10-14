package ir.erp.common;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
        .anyRequest().authenticated())
      .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
      .build();
  }
  @Bean
  JwtDecoder jwtDecoder(@Value("${erp.jwt.secret}") String secret) {
    byte[] bytes=secret.getBytes(StandardCharsets.UTF_8);
    if(bytes.length<32) throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
    NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(new SecretKeySpec(bytes,"HmacSHA256"))
       .macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("erp-local"));
    return decoder;
  }
  static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter=new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
      java.util.List<org.springframework.security.core.GrantedAuthority> result=new java.util.ArrayList<>();
      java.util.List<String> roles=jwt.getClaimAsStringList("roles");
      if(roles!=null) for(String role:roles) result.add(new SimpleGrantedAuthority("ROLE_"+role));
      return result;
    });
    return converter;
  }
}
