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

import brave.http.HttpResponseParser;
import brave.http.HttpTags;
import kamon.apm.sleuth.KamonApmSpanReporter;
import kamon.apm.sleuth.KamonApmSpanReporterConfig;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.autoconfig.TraceConfiguration;
import org.springframework.cloud.sleuth.autoconfig.zipkin2.ZipkinAutoConfiguration;
import org.springframework.cloud.sleuth.instrument.web.HttpServerResponseParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(TraceConfiguration.class)
@ConditionalOnClass({KamonApmMeterRegistry.class, KamonApmSpanReporter.class})
@EnableConfigurationProperties(KamonApmProperties.class)
public class KamonApmSpansExportConfiguration {

  @Bean(name = ZipkinAutoConfiguration.REPORTER_BEAN_NAME)
  @ConditionalOnMissingBean
  KamonApmSpanReporter kamonApmSpanReporter(KamonApmConfig kamonApmConfig) {
    return new KamonApmSpanReporter(kamonApmConfig, new KamonApmSpanReporterConfig() {
      @Override
      public int bufferSize() {
        return KamonApmSpanReporterConfig.super.bufferSize();
      }

      @Override
      public Duration flushInterval() {
        return KamonApmSpanReporterConfig.super.flushInterval();
      }
    });
  }
  
  @Bean(name = HttpServerResponseParser.NAME)
  public HttpResponseParser httpServerResponseParser() {
    return (response, context, span) -> {
      HttpResponseParser.DEFAULT.parse(response, context, span);

      // The default parser doesn't add http.status_code when it is a 2xx, but we want to have that tag
      // whenever it is available. We also want to add http.route to have an easy correlation between
      // HTTP server spans and the corresponding metrics exported via Micrometer
      span
        .tag(HttpTags.ROUTE.key(), response.route())
        .tag(HttpTags.STATUS_CODE.key(), String.valueOf(response.statusCode()));

    };
  }
}