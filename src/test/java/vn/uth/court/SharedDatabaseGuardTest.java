package vn.uth.court;
import static org.assertj.core.api.Assertions.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.env.PropertiesPropertySource;

class SharedDatabaseGuardTest {
  MockEnvironment shared() {
    var env=new MockEnvironment();env.setActiveProfiles("shared");
    return env.withProperty("spring.datasource.url","jdbc:mysql://team.aivencloud.com:23004/defaultdb?sslMode=VERIFY_IDENTITY&trustCertificateKeyStoreUrl=file:/tmp/ca.p12&trustCertificateKeyStoreType=PKCS12")
        .withProperty("spring.datasource.username","san_nha_member_01").withProperty("spring.flyway.enabled","false")
        .withProperty("spring.jpa.hibernate.ddl-auto","validate").withProperty("app.storage.mode","database")
        .withProperty("app.demo.seed","false").withProperty("app.owner.create","false");
  }
  @Test void realSharedPropertiesUseRemoteTlsAndLocalHttpWithoutFlyway() {
    new ApplicationContextRunner().withUserConfiguration(SharedDatabaseGuard.class)
      .withInitializer(c -> {
        c.getEnvironment().setActiveProfiles("shared");
        try {c.getEnvironment().getPropertySources().addLast(new PropertiesPropertySource("shared",
            PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-shared.properties"))));}
        catch(Exception e){throw new IllegalStateException(e);}
      })
      .withPropertyValues("SHARED_DB_USERNAME=san_nha_member_01","SHARED_DB_PASSWORD=test-only",
          "SHARED_DB_TRUSTSTORE_URL=file:/tmp/ca.p12")
      .run(c -> {
        assertThat(c).hasNotFailed();
        var e=c.getEnvironment();
        assertThat(e.getProperty("spring.datasource.url")).contains(".aivencloud.com:23004/defaultdb","sslMode=VERIFY_IDENTITY").doesNotContain("localhost","jdbc:h2");
        assertThat(e.getProperty("server.servlet.session.cookie.secure")).isEqualTo("false");
        assertThat(e.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(e.getProperty("app.storage.mode")).isEqualTo("database");
      });
  }
  @ParameterizedTest @ValueSource(strings={"jdbc:h2:mem:demo","jdbc:mysql://localhost/defaultdb","jdbc:mysql://127.0.0.1/defaultdb",
      "jdbc:mysql://team.aivencloud.com/defaultdb?sslMode=REQUIRED","jdbc:mysql://team.aivencloud.com/defaultdb?sslMode=DISABLED"})
  void rejectsLocalOrUnverifiedConnections(String url) {
    assertThatThrownBy(()->SharedDatabaseGuard.validate(shared().withProperty("spring.datasource.url",url)))
        .isInstanceOf(IllegalStateException.class);
  }
  @ParameterizedTest @ValueSource(strings={"spring.flyway.enabled","app.demo.seed","app.owner.create"})
  void rejectsStartupMutationFlags(String property) {
    assertThatThrownBy(()->SharedDatabaseGuard.validate(shared().withProperty(property,"true")))
        .isInstanceOf(IllegalStateException.class);
  }
  @Test void refusesAdminAndMixedProfiles() {
    assertThatThrownBy(()->SharedDatabaseGuard.validate(shared().withProperty("spring.datasource.username","avnadmin")))
        .isInstanceOf(IllegalStateException.class);
    var env=shared();env.setActiveProfiles("shared","demo");
    assertThatThrownBy(()->SharedDatabaseGuard.validate(env)).isInstanceOf(IllegalStateException.class);
  }
  @Test void renderMigrationRequiresSeparateNonAdminCredentials() {
    var env=new MockEnvironment();env.setActiveProfiles("render");
    env.withProperty("spring.flyway.enabled","true").withProperty("spring.datasource.username","san_nha_render")
        .withProperty("spring.flyway.password","test-only");
    assertThatThrownBy(()->SharedDatabaseGuard.validate(env)).isInstanceOf(IllegalStateException.class);
    env.withProperty("spring.flyway.user","san_nha_render");
    assertThatThrownBy(()->SharedDatabaseGuard.validate(env)).isInstanceOf(IllegalStateException.class);
    env.withProperty("spring.flyway.user","san_nha_migrator");
    assertThatCode(()->SharedDatabaseGuard.validate(env)).doesNotThrowAnyException();
  }
}
