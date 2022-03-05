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

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.push.PushRegistryConfig;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getUrlString;

public interface KamonApmConfig extends StepRegistryConfig {

  @Override
  default String prefix() {
    return "kamon-apm";
  }

  default String baseUrl() {
    return getUrlString(this, "baseUrl").orElse("https://ingestion.apm.kamon.io/");
  }

  default String apiKey() {
    return getString(this, "apiKey").required().get();
  }

  default String applicationName() {
    return getString(this, "applicationName").orElse("micrometer-app");
  }

  default String host() {
    return getString(this, "host").orElseGet(() -> {
      try {
        return InetAddress.getLocalHost().getHostName();
      } catch (Exception e) {
        return "localhost";
      }
    });
  }

  default String instance() {
    return getString(this, "instance").orElseGet(() -> ManagementFactory.getRuntimeMXBean().getName());
  }

  @Override
  default Validated<?> validate() {
    return checkAll(this,
        c -> PushRegistryConfig.validate(c),
        checkRequired("apiKey", KamonApmConfig::apiKey)
    );
  }
}
