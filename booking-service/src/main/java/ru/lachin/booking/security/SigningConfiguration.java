package ru.lachin.booking.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class SigningConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  JwtEncoder encoder(
      RSAPublicKey publicKey, @Value("${security.jwt.private-key}") Resource privateKey)
      throws IOException {
    try (var in = privateKey.getInputStream()) {
      var key =
          new RSAKey.Builder(publicKey).privateKey(RsaKeyConverters.pkcs8().convert(in)).build();
      return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
  }
}
