package io.quarkus.it.kubernetes;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.AbstractObjectAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class OpenshiftV4DeploymentConfigTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .withApplicationRoot((jar) -> jar.addClasses(GreetingResource.class))
            .setApplicationName("openshift-v4-deploymentconfig")
            .setApplicationVersion("0.1-SNAPSHOT")
            .withConfigurationResource("openshift-v4.properties")
            .overrideConfigKey("quarkus.openshift.deployment-kind", "deployment-config");

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void assertGeneratedResources() throws IOException {
        Path kubernetesDir = prodModeTestResults.getBuildDir().resolve("kubernetes");
        assertThat(kubernetesDir)
                .isDirectoryContaining(p -> p.getFileName().endsWith("openshift.json"))
                .isDirectoryContaining(p -> p.getFileName().endsWith("openshift.yml"))
                .satisfies(p -> assertThat(p.toFile().listFiles()).hasSize(2));
        List<HasMetadata> openshiftList = DeserializationUtil
                .deserializeAsList(kubernetesDir.resolve("openshift.yml"));

        assertThat(openshiftList).filteredOn(h -> "DeploymentConfig".equals(h.getKind())).singleElement().satisfies(h -> {
            assertThat(h.getMetadata()).satisfies(m -> {
                assertThat(m.getName()).isEqualTo("openshift-v4-deploymentconfig");
                assertThat(m.getLabels().get("app.openshift.io/runtime")).isEqualTo("test");
                assertThat(m.getLabels().get("app.kubernetes.io/name")).isEqualTo("openshift-v4-deploymentconfig");
                assertThat(m.getLabels().get("app")).isNull();
                assertThat(m.getNamespace()).isNull();
            });
            AbstractObjectAssert<?, ?> specAssert = assertThat(h).extracting("spec");
            specAssert.extracting("selector").isInstanceOfSatisfying(Map.class, selectorsMap -> {
                assertThat(selectorsMap).containsOnly(entry("app.kubernetes.io/name", "openshift-v4-deploymentconfig"),
                        entry("app.kubernetes.io/version", "0.1-SNAPSHOT"));
            });
        });

        assertThat(openshiftList).filteredOn(h -> "Service".equals(h.getKind())).singleElement().satisfies(h -> {
            assertThat(h).isInstanceOfSatisfying(Service.class, s -> {
                assertThat(s.getMetadata()).satisfies(m -> {
                    assertThat(m.getNamespace()).isNull();
                    assertThat(m.getLabels().get("app.kubernetes.io/name")).isEqualTo("openshift-v4-deploymentconfig");
                    assertThat(m.getLabels().get("app")).isNull();
                });

                assertThat(s.getSpec()).satisfies(spec -> {
                    assertThat(spec.getSelector()).containsOnly(
                            entry("app.kubernetes.io/name", "openshift-v4-deploymentconfig"),
                            entry("app.kubernetes.io/version", "0.1-SNAPSHOT"));
                });
            });
        });
    }
}
