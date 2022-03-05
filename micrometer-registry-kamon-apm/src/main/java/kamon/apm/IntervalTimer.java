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

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

public class IntervalTimer extends AbstractTimer {
  private final IntervalDistributionRecorder recorder = new IntervalDistributionRecorder();

  public IntervalTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
                       PauseDetector pauseDetector, TimeUnit baseTimeUnit, boolean supportsAggregablePercentiles) {

    super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, supportsAggregablePercentiles);
  }

  @Override
  protected void recordNonNegative(long amount, TimeUnit unit) {
    recorder.record(unit.toNanos(amount));
  }

  @Override
  public long count() {
    return recorder.count();
  }

  @Override
  public double totalTime(TimeUnit unit) {
    return TimeUtils.nanosToUnit(recorder.sum(), unit);
  }

  @Override
  public double max(TimeUnit unit) {
    return TimeUtils.nanosToUnit(recorder.max(), unit);
  }

  /**
   * Encodes the data available into a byte buffer
   *
   * @param buffer The target buffer for the histogram data
   */
  public synchronized void encodeAndReset(ByteBuffer buffer) {
    recorder.encodeAndReset(buffer);
  }
}
