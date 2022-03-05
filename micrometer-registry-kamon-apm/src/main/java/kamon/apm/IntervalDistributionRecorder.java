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

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramIterationValue;
import org.HdrHistogram.Recorder;

import java.nio.ByteBuffer;
import java.util.Iterator;

public class IntervalDistributionRecorder {
  private final Recorder recorder = new Recorder(2);
  private Histogram recyclableHistogram = null;

  public void record(long value) {
    recorder.recordValue(value);
  }

  /**
   * @return The number of values recorded in the last complete time interval
   */
  public long count() {
    return recyclableHistogram == null ? 0 : recyclableHistogram.getTotalCount();
  }

  /**
   * @return The sum of all values recorded in the last complete time interval
   */
  public long sum() {
    long sum = 0;
    if(recyclableHistogram != null) {
      Iterator<HistogramIterationValue> iterator = recyclableHistogram.allValues().iterator();
      while(iterator.hasNext()) {
        HistogramIterationValue v = iterator.next();
        sum += v.getCountAtValueIteratedTo() * v.getValueIteratedTo();
      }
    }

    return sum;
  }

  /**
   * @return The maximum value recorded in the last complete time interval
   */
  public long max() {
    long max = 0;
    if(recyclableHistogram != null) {
      max = recyclableHistogram.getMaxValue();
    }

    return max;
  }

  /**
   * Encodes all the data recorded in the current time interval using the HDR Histogram
   * binary format and starts a new time interval with an empty histogram.
   *
   * @param buffer The target buffer for the histogram data
   */
  public void encodeAndReset(ByteBuffer buffer) {
    recyclableHistogram = recorder.getIntervalHistogram(recyclableHistogram);
    recyclableHistogram.encodeIntoByteBuffer(buffer);
  }
}
