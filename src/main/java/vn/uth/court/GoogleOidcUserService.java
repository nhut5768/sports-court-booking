package vn.uth.court;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class GoogleOidcUserService extends OidcUserService {
  private final GoogleAccountService accounts;
  public GoogleOidcUserService(GoogleAccountService accounts) { this.accounts = accounts; }

  @Override
  public OidcUser loadUser(OidcUserRequest request) {
    return localPrincipal(super.loadUser(request));
  }

  OidcUser localPrincipal(OidcUser google) {
    Account account;
    try {
      account = accounts.resolve(google);
    } catch (DataIntegrityViolationException ex) {
      throw GoogleAccountService.failure("google_registration_conflict");
    }
    final String email = account.getEmail();
    return new DefaultOidcUser(
        List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole())),
        google.getIdToken(), google.getUserInfo()) {
      @Override
      public String getName() { return email; }
    };
  }
}
