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

import org.springframework.boot.actuate.autoconfigure.metrics.export.properties.PushRegistryPropertiesConfigAdapter;

public class KamonApmPropertiesConfigAdapter extends PushRegistryPropertiesConfigAdapter<KamonApmProperties>
  implements KamonApmConfig {


  public KamonApmPropertiesConfigAdapter(KamonApmProperties kamonApmProperties) {
    super(kamonApmProperties);
  }

  @Override
  public String prefix() {
    return "management.metrics.export.kamon-apm";
  }

  @Override
  public String apiKey() {
    return get(KamonApmProperties::getApiKey, KamonApmConfig.super::apiKey);
  }

  @Override
  public String applicationName() {
    return get(KamonApmProperties::getApplicationName, KamonApmConfig.super::applicationName);
  }

  @Override
  public String baseUrl() {
    return get(KamonApmProperties::getBaseUrl, KamonApmConfig.super::baseUrl);
  }

  @Override
  public String host() {
    return get(KamonApmProperties::getHost, KamonApmConfig.super::host);
  }

  @Override
  public String instance() {
    return get(KamonApmProperties::getInstance, KamonApmConfig.super::instance);
  }
}
