/**
 * Copyright (c) 2019 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at:
 *
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.jkube.quarkus;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.eclipse.jkube.kit.common.Dependency;
import org.eclipse.jkube.kit.common.JavaProject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.eclipse.jkube.quarkus.QuarkusUtils.concatPath;
import static org.eclipse.jkube.quarkus.QuarkusUtils.extractPort;
import static org.eclipse.jkube.quarkus.QuarkusUtils.findQuarkusVersion;
import static org.eclipse.jkube.quarkus.QuarkusUtils.getQuarkusConfiguration;
import static org.eclipse.jkube.quarkus.QuarkusUtils.isStartupEndpointSupported;
import static org.eclipse.jkube.quarkus.QuarkusUtils.resolveCompleteQuarkusHealthRootPath;
import static org.eclipse.jkube.quarkus.QuarkusUtils.resolveQuarkusLivenessPath;
import static org.eclipse.jkube.quarkus.QuarkusUtils.resolveQuarkusStartupPath;

public class QuarkusUtilsTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private JavaProject javaProject;

  @Before
  public void setUp() throws IOException {
    javaProject = JavaProject.builder()
        .properties(new Properties())
        .outputDirectory(temporaryFolder.newFolder())
        .build();
  }

  @Test
  public void extractPort_noProfileAndNoPort_shouldReturnDefault() {
    // When
    final String result = extractPort(javaProject, new Properties(), "80");
    // Then
    assertThat(result).isEqualTo("80");
  }

  @Test
  public void extractPort_noProfileAndPort_shouldReturnPort() {
    // Given
    final Properties properties = new Properties();
    properties.put("quarkus.http.port", "1337");
    // When
    final String result = extractPort(javaProject, properties, "80");
    // Then
    assertThat(result).isEqualTo("1337");
  }

  @Test
  public void extractPort_inactiveProfileAndPort_shouldReturnPort() {
    // Given
    final Properties properties = new Properties();
    properties.put("quarkus.http.port", "1337");
    properties.put("%dev.quarkus.http.port", "31337");
    // When
    final String result = extractPort(javaProject, properties, "80");
    // Then
    assertThat(result).isEqualTo("1337");
  }

  @Test
  public void extractPort_activeProfileAndPort_shouldReturnProfilePort() {
    // Given
    final Properties properties = new Properties();
    properties.put("quarkus.http.port", "1337");
    properties.put("%dev.quarkus.http.port", "31337");
    javaProject.getProperties().put("quarkus.profile", "dev");
    // When
    final String result = extractPort(javaProject, properties, "80");
    // Then
    assertThat(result).isEqualTo("31337");
  }

  @Test
  public void getQuarkusConfiguration_propertiesAndYamlProjectProperties_shouldUseProjectProperties() {
    // Given
    javaProject.getProperties().put("quarkus.http.port", "42");
    javaProject.setCompileClassPathElements(Arrays.asList(
        QuarkusUtilsTest.class.getResource("/utils-test/config/yaml/").getPath(),
        QuarkusUtilsTest.class.getResource("/utils-test/config/properties/").getPath()
    ));
    // When
    final Properties props = getQuarkusConfiguration(javaProject);
    // Then
    assertThat(props).containsOnly(
        entry("quarkus.http.port", "42"),
        entry("%dev.quarkus.http.port", "8082"));
  }

  @Test
  public void getQuarkusConfiguration_propertiesAndYaml_shouldUseProperties() {
    // Given
    javaProject.setCompileClassPathElements(Arrays.asList(
        QuarkusUtilsTest.class.getResource("/utils-test/config/yaml/").getPath(),
        QuarkusUtilsTest.class.getResource("/utils-test/config/properties/").getPath()
    ));
    // When
    final Properties props = getQuarkusConfiguration(javaProject);
    // Then
    assertThat(props).containsOnly(
        entry("quarkus.http.port", "1337"),
        entry("%dev.quarkus.http.port", "8082"));
  }

  @Test
  public void getQuarkusConfiguration_yamlOnly_shouldUseYaml() {
    // Given
    javaProject.setCompileClassPathElements(Collections.singletonList(
        QuarkusUtilsTest.class.getResource("/utils-test/config/yaml/").getPath()
    ));
    // When
    final Properties props = getQuarkusConfiguration(javaProject);
    // Then
    assertThat(props).containsOnly(
        entry("quarkus.http.port", "31337"),
        entry("%dev.quarkus.http.port", "13373"));
  }

  @Test
  public void getQuarkusConfiguration_noConfigFiles_shouldReturnEmpty() {
    // Given
    javaProject.setCompileClassPathElements(Collections.singletonList(
        QuarkusUtilsTest.class.getResource("/").getPath()
    ));
    // When
    final Properties props = getQuarkusConfiguration(javaProject);
    // Then
    assertThat(props).isEmpty();
  }

  @Test
  public void findQuarkusVersion_noDependency_shouldReturnEmpty() {
    // Given
    javaProject.setDependencies(Collections.emptyList());
    // When
    final String result = findQuarkusVersion(javaProject);
    // Then
    assertThat(result).isNull();
  }

  @Test
  public void findQuarkusVersion_withQuarkusUniverseDependency_shouldReturnValidVersion() {
    // Given
    javaProject.setDependencies(quarkusDependencyWithVersion("2.0.1.Final"));
    // When
    final String result = findQuarkusVersion(javaProject);
    // Then
    assertThat(result).isEqualTo("2.0.1.Final");
  }

  @Test
  public void resolveCompleteQuarkusHealthRootPath_withHealthRootPathSet_shouldReturnValidPath() {
    // Given
    Properties properties = new Properties();
    properties.setProperty("quarkus.http.non-application-root-path", "q");
    properties.setProperty("quarkus.smallrye-health.root-path", "health");
    properties.setProperty("quarkus.http.root-path", "/");
    javaProject.setProperties(properties);

    // When
    String resolvedHealthPath = resolveCompleteQuarkusHealthRootPath(javaProject, "");

    // Then
    assertThat(resolvedHealthPath).isNotEmpty().isEqualTo("/q/health");
  }

  @Test
  public void resolveCompleteQuarkusHealthRootPath_withHealthRootPathSetAbsolute_shouldReturnValidPath() {
    // Given
    Properties properties = new Properties();
    properties.setProperty("quarkus.smallrye-health.root-path", "/health");
    javaProject.setProperties(properties);
    javaProject.setDependencies(quarkusDependencyWithVersion("1.13.7.Final"));

    // When
    String resolvedHealthPath = resolveCompleteQuarkusHealthRootPath(javaProject, "");

    // Then
    assertThat(resolvedHealthPath).isNotEmpty().isEqualTo("/health");
  }

  @Test
  public void resolveCompleteQuarkusHealthRootPath_withOldQuarkusVersion_shouldReturnValidPath() {
    // Given
    Properties properties = new Properties();
    properties.setProperty("quarkus.smallrye-health.root-path", "/health");
    properties.setProperty("quarkus.http.root-path", "/root");
    javaProject.setProperties(properties);
    javaProject.setDependencies(quarkusDependencyWithVersion("1.10.5.Final"));

    // When
    String resolvedHealthPath = resolveCompleteQuarkusHealthRootPath(javaProject, "");

    // Then
    assertThat(resolvedHealthPath).isNotEmpty().isEqualTo("/root/health");
  }

  @Test
  public void resolveCompleteQuarkusHealthRootPath_withPostPathResolutionChangesQuarkusVersion_shouldReturnAbsolutePath() {
    // Given
    Properties properties = new Properties();
    properties.setProperty("quarkus.smallrye-health.root-path", "/health");
    properties.setProperty("quarkus.http.root-path", "/root");
    javaProject.setProperties(properties);
    javaProject.setDependencies(quarkusDependencyWithVersion("1.13.7.Final"));

    // When
    String resolvedHealthPath = resolveCompleteQuarkusHealthRootPath(javaProject, "");

    // Then
    assertThat(resolvedHealthPath).isNotEmpty().isEqualTo("/health");
  }

  @Test
  public void resolveCompleteQuarkusHealthRootPath_withQuarkus2_shouldReturnAbsoluteNonApplicationRootPath() {
    // Given
    Properties properties = new Properties();
    properties.setProperty("quarkus.http.non-application-root-path", "/q");
    properties.setProperty("quarkus.smallrye-health.root-path", "health");
    javaProject.setProperties(properties);
    javaProject.setDependencies(quarkusDependencyWithVersion("1.13.7.Final"));

    // When
    String resolvedHealthPath = resolveCompleteQuarkusHealthRootPath(javaProject, "");

    // Then
    assertThat(resolvedHealthPath).isNotEmpty().isEqualTo("/q/health");
  }

  @Test
  public void resolveCompleteQuarkusHealthRootPath_withQuarkus2_shouldReturnCompleteHealthPath() {
    // Given
    Properties properties = new Properties();
    properties.setProperty("quarkus.http.root-path", "/");
    properties.setProperty("quarkus.http.non-application-root-path", "q");
    properties.setProperty("quarkus.smallrye-health.root-path", "health");
    javaProject.setProperties(properties);
    javaProject.setDependencies(quarkusDependencyWithVersion("1.13.7.Final"));

    // When
    String resolvedHealthPath = resolveCompleteQuarkusHealthRootPath(javaProject, "");

    // Then
    assertThat(resolvedHealthPath).isNotEmpty().isEqualTo("/q/health");
  }

  @Test
  public void resolveQuarkusLivenessPath_withLivenessPathSet_shouldReturnValidPath() {
    // Given
    Properties properties = new Properties();
    properties.setProperty("quarkus.smallrye-health.liveness-path", "liveness");
    javaProject.setProperties(properties);

    // When
    String resolvedHealthPath = resolveQuarkusLivenessPath(javaProject);

    // Then
    assertThat(resolvedHealthPath).isNotEmpty().isEqualTo("liveness");
  }

  @Test
  public void resolveQuarkusStartupPath_withStartupPathSet_shouldReturnValidPath() {
    // Given
    Properties properties = new Properties();
    properties.setProperty("quarkus.smallrye-health.startup-path", "startup");
    javaProject.setProperties(properties);
    // When
    String resolvedStartupPath = resolveQuarkusStartupPath(javaProject);
    // Then
    assertThat(resolvedStartupPath).isNotEmpty()
            .isEqualTo("startup");
  }

  @Test
  public void isStartupEndpointSupported_withQuarkusVersionBefore2_1_shouldReturnFalse() {
    // Given
    javaProject.setDependencies(quarkusDependencyWithVersion("2.0.3.Final"));

    // When
    boolean result = isStartupEndpointSupported(javaProject);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  public void isStartupEndpointSupported_withQuarkusVersionAfter2_1_shouldReturnTrue() {
    // Given
    javaProject.setDependencies(quarkusDependencyWithVersion("2.9.2.Final"));

    // When
    boolean result = isStartupEndpointSupported(javaProject);

    // Then
    assertThat(result).isTrue();
  }

  @Test
  public void concatPath_withEmptyRootAndPrefixed() {
    assertThat(concatPath("/", "/liveness")).isEqualTo("/liveness");
  }

  @Test
  public void concatPath_withRootAndFiltered() {
    assertThat(concatPath("/root", null, "/", "q", "liveness")).isEqualTo("/root/q/liveness");
  }

  private List<Dependency> quarkusDependencyWithVersion(String version) {
    return Collections.singletonList(Dependency.builder()
            .groupId("io.quarkus")
            .artifactId("quarkus-universe-bom")
            .version(version)
            .build());
  }
}
