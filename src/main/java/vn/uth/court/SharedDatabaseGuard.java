package vn.uth.court;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Validate before any datasource, seed or owner bootstrap bean is instantiated. */
@Configuration
@Profile({"shared", "render"})
public class SharedDatabaseGuard {
  @Bean
  static BeanFactoryPostProcessor sharedDatabasePolicy(Environment env) {
    return factory -> validate(env);
  }
  static void validate(Environment env) {
    boolean shared=Arrays.asList(env.getActiveProfiles()).contains("shared");
    if(shared) {
      if(env.getActiveProfiles().length!=1) fail("Use shared alone; do not combine it with demo/mysql/prod/render.");
      if(env.getProperty("spring.flyway.enabled",Boolean.class,true)) fail("Shared members cannot run Flyway.");
      if(!"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto"))) fail("Shared schema must be validate-only.");
      if(env.getProperty("app.demo.seed",Boolean.class,true)||env.getProperty("app.owner.create",Boolean.class,true)) fail("Shared bootstrap must be disabled.");
      if(!"database".equals(env.getProperty("app.storage.mode"))) fail("Shared uploads must use MySQL.");
      if("avnadmin".equalsIgnoreCase(env.getProperty("spring.datasource.username",""))) fail("Use an individual member database account.");
      String url=env.getProperty("spring.datasource.url","");
      try {
        if(!url.startsWith("jdbc:mysql://")) fail("Shared requires remote Aiven MySQL.");
        URI uri=URI.create(url.substring(5));
        if(uri.getHost()==null||!uri.getHost().endsWith(".aivencloud.com")) fail("Shared requires an Aiven hostname.");
        var parameters=new HashMap<String,String>();
        for(String item:uri.getRawQuery().split("&")) {
          String[] pair=item.split("=",2);
          String key=URLDecoder.decode(pair[0],StandardCharsets.UTF_8);
          if(pair.length!=2||parameters.put(key,URLDecoder.decode(pair[1],StandardCharsets.UTF_8))!=null) fail("Duplicate or malformed JDBC parameter.");
        }
        if(!"VERIFY_IDENTITY".equals(parameters.get("sslMode"))||
            !"PKCS12".equals(parameters.get("trustCertificateKeyStoreType"))||
            !parameters.getOrDefault("trustCertificateKeyStoreUrl","").startsWith("file:"))
          fail("Shared requires VERIFY_IDENTITY and a local CA truststore.");
      } catch(IllegalArgumentException|NullPointerException ex) { fail("Invalid shared JDBC/TLS configuration."); }
    } else if(env.getProperty("spring.flyway.enabled",Boolean.class,false)) {
      String migration=env.getProperty("spring.flyway.user","");
      if(migration.isBlank()||migration.equalsIgnoreCase("avnadmin")||
          migration.equals(env.getProperty("spring.datasource.username"))||
          env.getProperty("spring.flyway.password","").isBlank())
        fail("Render migrations require a separate deployment database account.");
    }
  }
  private static void fail(String message) { throw new IllegalStateException(message); }
}
