/*
 * Copyright 2013-2022 The Kamon Project <https://kamon.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kamon.apm;

import io.micrometer.core.instrument.Clock;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.ConditionalOnEnabledMetricsExport;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore({ CompositeMeterRegistryAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class })
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(KamonApmMeterRegistry.class)
@ConditionalOnEnabledMetricsExport("kamon-apm")
@EnableConfigurationProperties(KamonApmProperties.class)
public class KamonApmMetricsExportConfiguration {

  private final KamonApmProperties kamonApmProperties;

  public KamonApmMetricsExportConfiguration(KamonApmProperties kamonApmProperties) {
    this.kamonApmProperties = kamonApmProperties;
  }

  @Bean
  @ConditionalOnMissingBean
  public KamonApmConfig kamonApmConfig() {
    return new KamonApmPropertiesConfigAdapter(kamonApmProperties);
  }

  @Bean
  @ConditionalOnMissingBean
  KamonApmMeterRegistry kamonApmMeterRegistry(KamonApmConfig kamonApmConfig, Clock clock) {
    return new KamonApmMeterRegistry(kamonApmConfig, clock);
  }
}