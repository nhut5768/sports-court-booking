package vn.uth.court;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;

@Configuration
@Conditional(GoogleOAuthConfig.CredentialsPresent.class)
public class GoogleOAuthConfig {
  static class CredentialsPresent implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return StringUtils.hasText(context.getEnvironment().getProperty("GOOGLE_CLIENT_ID"))
          && StringUtils.hasText(context.getEnvironment().getProperty("GOOGLE_CLIENT_SECRET"));
    }
  }
  @Bean
  ClientRegistrationRepository googleClients(org.springframework.core.env.Environment env) {
    return new InMemoryClientRegistrationRepository(
        CommonOAuth2Provider.GOOGLE.getBuilder("google")
            .clientId(env.getRequiredProperty("GOOGLE_CLIENT_ID"))
            .clientSecret(env.getRequiredProperty("GOOGLE_CLIENT_SECRET"))
            .scope("openid", "profile", "email")
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .build());
  }
}
