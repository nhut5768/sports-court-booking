package vn.uth.court;

import java.util.Locale;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleAccountService {
  private final AccountRepository accounts;
  public GoogleAccountService(AccountRepository accounts) { this.accounts = accounts; }

  @Transactional
  public Account resolve(OidcUser user) {
    String sub = user.getSubject();
    String email = user.getEmail();
    if (sub == null || sub.isBlank() || sub.length() > 64
        || !Boolean.TRUE.equals(user.getEmailVerified())
        || email == null || email.isBlank() || email.length() > 190) {
      throw failure("google_identity");
    }
    Account existing = accounts.findByGoogleSub(sub).orElse(null);
    if (existing != null) {
      if(!"CUSTOMER".equals(existing.getRole())) throw failure("google_role");
      if(existing.isLocked()) throw failure("account_locked");
      return existing;
    }
    email = email.trim().toLowerCase(Locale.ROOT);
    // A verified email alone is never sufficient to link an existing account.
    if (accounts.findByEmailIgnoreCase(email).isPresent()) {
      throw failure("google_email_conflict");
    }
    Account account = new Account();
    account.setGoogleSub(sub);
    account.setEmail(email);
    String name = user.getFullName();
    if (name == null || name.isBlank()) name = email;
    account.setName(name.substring(0, Math.min(name.length(), 100)));
    account.setRole("CUSTOMER");
    // Not a BCrypt hash: password authentication cannot use this value.
    account.setPassword("{google-only}" + UUID.randomUUID());
    return accounts.saveAndFlush(account);
  }

  static OAuth2AuthenticationException failure(String code) {
    return new OAuth2AuthenticationException(new OAuth2Error(code));
  }
}
