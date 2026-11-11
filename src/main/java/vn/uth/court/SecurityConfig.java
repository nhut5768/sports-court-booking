package vn.uth.court;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityConfig {

  @Bean
  Clock clock() {
    return Clock.system(AppConstants.ZONE);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** Đăng nhập bằng email hoặc số điện thoại; tên đăng nhập trong phiên luôn là email. */
  @Bean
  UserDetailsService userDetailsService(AccountService accounts) {
    return login ->
        accounts
            .findByLogin(login)
            .map(
                account ->
                    User.withUsername(account.getEmail())
                        .password(account.getPassword())
                        .roles(account.getRole())
                        .disabled(account.isLocked())
                        .build())
            .orElseThrow(
                () -> new UsernameNotFoundException("Không tìm thấy tài khoản"));
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http,
      org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> clients,
      GoogleOidcUserService googleUsers, AccountRepository accounts, RoleLoginSuccessHandler success) throws Exception {
    http.authorizeHttpRequests(
            authorization ->
                authorization
                    .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                    .requestMatchers(
                        "/",
                        "/courts/**",
                        "/login",
                        "/register",
                        "/register/customer",
                        "/register/owner",
                        "/css/**",
                        "/vendor/**",
                        "/images/**",
                        "/media/**",
                        "/health",
                        "/api/assistant/**",
                        "/error")
                    .permitAll()
                    .requestMatchers("/api/messages/**", "/messages/**", "/messages")
                    .hasAnyRole("CUSTOMER", "OWNER")
                    .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                    .requestMatchers("/owner", "/owner/**")
                    .hasRole("OWNER")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form.loginPage("/login")
                    .successHandler(success)
                    .permitAll())
        .logout(
            logout ->
                logout.logoutSuccessUrl("/?logout"))
        .headers(
            headers ->
                headers
                    .contentTypeOptions(options -> {})
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));

    http.exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
        new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED),
        request -> request.getRequestURI().startsWith(request.getContextPath() + "/api/messages/")));
    var registrations = clients.getIfAvailable();
    if (registrations != null) {
      var resolver = new org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver(
          registrations, "/oauth2/authorization");
      resolver.setAuthorizationRequestCustomizer(builder ->
          builder.additionalParameters(parameters -> parameters.put("prompt", "select_account")));
      http.oauth2Login(oauth -> oauth
          .clientRegistrationRepository(registrations)
          .loginPage("/login")
          .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(resolver))
          .userInfoEndpoint(info -> info.oidcUserService(googleUsers))
          .successHandler(success)
          .failureHandler((request, response, exception) -> {
            String code = exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException error
                ? error.getError().getErrorCode() : "";
            String reason = "google_email_conflict".equals(code) ? "email_conflict" : "failed";
            response.sendRedirect(request.getContextPath() + "/login?google_error=" + reason);
          }));
    }
    http.addFilterBefore(new AccountSessionFilter(accounts), org.springframework.security.web.access.intercept.AuthorizationFilter.class);
    return http.build();
  }
}
