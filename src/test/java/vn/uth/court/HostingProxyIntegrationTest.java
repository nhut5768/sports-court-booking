package vn.uth.court;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** Real embedded HTTP server: forwarded HTTPS must work for OAuth and secure sessions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "app.demo.seed=false",
    "GOOGLE_CLIENT_ID=hosting-test-client",
    "GOOGLE_CLIENT_SECRET=hosting-test-secret",
    "server.forward-headers-strategy=framework",
    "server.servlet.session.cookie.secure=true"
})
@ActiveProfiles("demo")
class HostingProxyIntegrationTest {
  @LocalServerPort int port;
  private HttpResponse<String> get(String path) throws Exception {
    try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
      return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
          .header("X-Forwarded-Proto", "https")
          .header("X-Forwarded-Host", "san-nha.example.org")
          .header("X-Forwarded-Port", "443").GET().build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }
  @Test void oauthUsesPublicHttpsCallbackInsteadOfInternalContainerAddress() throws Exception {
    var response = get("/oauth2/authorization/google");
    assertThat(response.statusCode()).isEqualTo(302);
    var location = URLDecoder.decode(response.headers().firstValue("Location").orElseThrow(),
        StandardCharsets.UTF_8);
    assertThat(location).startsWith("https://accounts.google.com/")
        .contains("redirect_uri=https://san-nha.example.org/login/oauth2/code/google")
        .doesNotContain("redirect_uri=http://", "127.0.0.1", ":8080");
  }
  @Test void loginBehindProxyKeepsSecureCsrfSessionAndRelativeChatEndpoints() throws Exception {
    var response = get("/login");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("_csrf", "/oauth2/authorization/google")
        .doesNotContain("http://localhost:");
    assertThat(response.headers().allValues("Set-Cookie")).anySatisfy(cookie ->
        assertThat(cookie).contains("JSESSIONID=", "Secure", "HttpOnly", "SameSite=Lax"));
    assertThat(get("/api/messages/owners").statusCode()).isEqualTo(401);
  }
}
