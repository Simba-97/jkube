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
package org.eclipse.jkube.kit.enricher.specific;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Probe;
import java.util.Properties;

import org.eclipse.jkube.kit.enricher.api.JKubeEnricherContext;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebAppHealthCheckEnricherTest {

    private JKubeEnricherContext context;
    @Before
    public void setUp() {
        context = mock(JKubeEnricherContext.class, RETURNS_DEEP_STUBS);
    }

    private void setupExpectations(Properties properties) {
        when(context.hasPlugin("org.apache.maven.plugins", "maven-war-plugin")).thenReturn(true);
        when(context.getProperties()).thenReturn(properties);
    }

    @Test
    public void noEnrichmentIfNoPath() {
        // given
        WebAppHealthCheckEnricher enricher = new WebAppHealthCheckEnricher(context);
        setupExpectations(new Properties());
        // when
        Probe probeLiveness = enricher.getLivenessProbe();
        Probe probeReadiness = enricher.getReadinessProbe();

        // then
        assertThat(probeLiveness).isNull();
        assertThat(probeReadiness).isNull();
    }

    @Test
    public void enrichmentWithDefaultsIfPath() {
        // given
        Properties properties = new Properties();
        properties.put("jkube.enricher.jkube-healthcheck-webapp.path", "/health");
        setupExpectations(properties);

        WebAppHealthCheckEnricher enricher = new WebAppHealthCheckEnricher(context);

        // when
        Probe probeLiveness = enricher.getLivenessProbe();
        Probe probeReadiness = enricher.getReadinessProbe();

        // then
        assertThat(probeLiveness)
            .isNotNull()
            .hasFieldOrPropertyWithValue("httpGet.port", new IntOrString(8080))
            .hasFieldOrPropertyWithValue("httpGet.scheme", "HTTP")
            .hasFieldOrPropertyWithValue("httpGet.path", "/health")
            .hasFieldOrPropertyWithValue("initialDelaySeconds", 180);
        assertThat(probeReadiness)
            .isNotNull()
            .hasFieldOrPropertyWithValue("httpGet.port", new IntOrString(8080))
            .hasFieldOrPropertyWithValue("httpGet.scheme", "HTTP")
            .hasFieldOrPropertyWithValue("httpGet.path", "/health")
            .hasFieldOrPropertyWithValue("initialDelaySeconds", 10);
    }
}
