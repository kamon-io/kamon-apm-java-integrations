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

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.nio.ByteBuffer;

public class IntervalDistributionSummary extends AbstractDistributionSummary {
  private final IntervalDistributionRecorder recorder = new IntervalDistributionRecorder();

  public IntervalDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale,
                                     boolean supportsAggregablePercentiles) {
    super(id, clock, distributionStatisticConfig, scale, supportsAggregablePercentiles);
  }

  @Override
  protected void recordNonNegative(double amount) {
    recorder.record((long)amount);
  }

  @Override
  public long count() {
    return recorder.count();
  }

  @Override
  public double totalAmount() {
    return recorder.sum();
  }

  @Override
  public double max() {
    return recorder.max();
  }

  public synchronized void encodeAndReset(ByteBuffer buffer) {
    recorder.encodeAndReset(buffer);
  }
}
