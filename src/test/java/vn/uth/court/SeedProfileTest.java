package vn.uth.court;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
class SeedProfileTest {
  @ParameterizedTest
  @ValueSource(strings={"mysql","prod","shared","render","hosting"})
  void realDatabaseProfilesNeverCreateDemoSeedEvenWhenEnabled(String profile) {
    new ApplicationContextRunner().withUserConfiguration(SeedData.class)
        .withInitializer(context->context.getEnvironment().setActiveProfiles("demo",profile))
        .withPropertyValues("app.demo.seed=true")
        .run(context->{assertThat(context).hasNotFailed();assertThat(context).doesNotHaveBean("seed");});
  }
}
