package ru.lachin.common;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtConfiguration {
  @Bean
  RSAPublicKey publicKey(@Value("${security.jwt.public-key}") Resource key) throws IOException {
    try (var in = key.getInputStream()) {
      return RsaKeyConverters.x509().convert(in);
    }
  }

  @Bean
  JwtDecoder jwtDecoder(
      RSAPublicKey key,
      @Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.audience}") String audience) {
    var decoder = NimbusJwtDecoder.withPublicKey(key).build();
    OAuth2TokenValidator<Jwt> aud =
        token ->
            token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), aud));
    return decoder;
  }
}
