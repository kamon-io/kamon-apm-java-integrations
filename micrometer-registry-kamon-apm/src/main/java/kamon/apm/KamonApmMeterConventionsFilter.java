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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.*;

public class KamonApmMeterConventionsFilter implements MeterFilter {

  @Override
  public Meter.Id map(Meter.Id id) {
    switch (id.getName()) {
      case "http.server.requests":
        Map<String, String> httpServerReplacementTags = new HashMap<>();
        httpServerReplacementTags.put("status", "http.status_code");
        httpServerReplacementTags.put("method", "http.method");
        httpServerReplacementTags.put("uri", "http.route");
        httpServerReplacementTags.put("exception", "exception.type");
        return replaceTags(id.withName("http.server.duration"), httpServerReplacementTags);

      case "http.client.requests":
        Map<String, String> httpClientReplacementTags = new HashMap<>();
        httpClientReplacementTags.put("status", "http.status_code");
        httpClientReplacementTags.put("clientName", "http.host");
        httpClientReplacementTags.put("method", "http.method");
        httpClientReplacementTags.put("uri", "http.route");
        return replaceTags(id.withName("http.client.duration"), httpClientReplacementTags);

      default:
        return id;

    }
  }

  private Meter.Id replaceTags(Meter.Id id, Map<String, String> replacementTags) {
    List<Tag> newTags = new ArrayList<>();

    for (Tag tag : id.getTagsAsIterable()) {
      String replacementTag = replacementTags.get(tag.getKey());
      if (replacementTag != null)
        newTags.add(Tag.of(replacementTag, tag.getValue()));
      else
        newTags.add(tag);
    }

    return id.replaceTags(newTags);
  }
}
