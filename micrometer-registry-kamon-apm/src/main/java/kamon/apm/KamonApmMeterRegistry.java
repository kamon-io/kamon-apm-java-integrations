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

import com.google.protobuf.ByteString;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
import kamon.apm.ingestion.v1.metrics.*;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class KamonApmMeterRegistry extends StepMeterRegistry {
  private final InternalLogger logger = InternalLoggerFactory.getInstance(KamonApmMeterRegistry.class);

  private final KamonApmConfig kamonApmConfig;
  private final String kamonApmIngestUrl;
  private final HttpSender httpSender;
  private final ByteBuffer tempBuffer;
  private long lastPublishedAt;

  public KamonApmMeterRegistry(KamonApmConfig config, Clock clock) {
    super(config, clock);
    this.kamonApmConfig = config;
    this.lastPublishedAt = clock.wallTime();
    this.tempBuffer = ByteBuffer.allocate(7300 * 4);
    this.httpSender = new HttpUrlConnectionSender(Duration.ofSeconds(3), Duration.ofSeconds(10));

    String cleanBaseUrl = config.baseUrl().endsWith("/") ? config.baseUrl() : config.baseUrl().concat("/");
    this.kamonApmIngestUrl = cleanBaseUrl.concat("v1/ingest");

    config().meterFilter(new KamonApmMeterConventionsFilter());
    start(new NamedThreadFactory("kamon-apm-meter-registry"));
  }

  @Override
  protected void publish() {
    long previousPublishedAt = lastPublishedAt;
    lastPublishedAt = clock.wallTime();

    try {
      MetricBatch.Builder metricBatchBuilder = MetricBatch.newBuilder()
        .setInterval(Interval.newBuilder()
          .setFrom(previousPublishedAt)
          .setTo(lastPublishedAt)
          .build())
        .setApiKey(kamonApmConfig.apiKey())
        .setService(kamonApmConfig.applicationName())
        .setHost(kamonApmConfig.host())
        .setInstance(kamonApmConfig.instance())
        .setAgent("micrometer");

      for (Meter meter : getMeters()) {
        meter.use(
          m -> writeGauge(m, metricBatchBuilder, tempBuffer),
          m -> writeCounter(m, metricBatchBuilder, tempBuffer),
          m -> writeTimer(m, metricBatchBuilder, tempBuffer),
          m -> writeDistributionSummary(m, metricBatchBuilder, tempBuffer),
          m -> writeLongTaskTimer(m, metricBatchBuilder, tempBuffer),
          m -> writeGauge(m, metricBatchBuilder, tempBuffer),
          m -> writeFunctionCounter(m, metricBatchBuilder, tempBuffer),
          m -> writeFunctionTimer(m, metricBatchBuilder, tempBuffer),
          m -> writeMeter(m, metricBatchBuilder, tempBuffer)
        );
      }

      HttpSender.Response response = httpSender.post(kamonApmIngestUrl)
        .withContent("application/octet-stream", metricBatchBuilder.build().toByteArray())
        .send();

      if(!response.isSuccessful()) {
        logger.error("Publishing metrics to Kamon APM failed with HTTP Status [{}]", response.code());
      }

    } catch (Throwable e) {
      logger.error("Failed to publish metrics to Kamon APM", e);
    }
  }




  private void writeGauge(Gauge gauge, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    long gaugeValue = (long) gauge.value();
    writeAsGauge(gauge.getId(), gaugeValue, metricBatchBuilder, tempBuffer);
  }

  private void writeAsGauge(Meter.Id id, long value, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    tempBuffer.clear();
    putLong(tempBuffer, -hdrHistogramIndexForValue(value));
    putLong(tempBuffer, 1);
    tempBuffer.flip();

    metricBatchBuilder.addMetrics(
      Metric.newBuilder()
        .setName(id.getName())
        .putAllTags(tagListToMap(id.getTags()))
        .setInstrumentType(InstrumentType.GAUGE)
        .setData(ByteString.copyFrom(tempBuffer))
        .build()
    );
  }

  private void writeCounter(Counter counter, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    long count = Math.round(counter.count());
    writeAsCounter(counter.getId(), count, metricBatchBuilder, tempBuffer);
  }

  private void writeAsCounter(Meter.Id id, long value, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    tempBuffer.clear();
    putLong(tempBuffer, value);
    tempBuffer.flip();

    metricBatchBuilder.addMetrics(
      Metric.newBuilder()
        .setName(id.getName())
        .putAllTags(tagListToMap(id.getTags()))
        .setInstrumentType(InstrumentType.COUNTER)
        .setData(ByteString.copyFrom(tempBuffer))
        .build()
    );
  }

  private void writeTimer(Timer timer, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    tempBuffer.clear();
    ((IntervalTimer) timer).encodeAndReset(tempBuffer);
    writeDistribution(timer.getId(), metricBatchBuilder, tempBuffer);
  }

  private void writeDistributionSummary(DistributionSummary ds, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    tempBuffer.clear();
    ((IntervalDistributionSummary) ds).encodeAndReset(tempBuffer);
    writeDistribution(ds.getId(), metricBatchBuilder, tempBuffer);
  }

  private void writeLongTaskTimer(LongTaskTimer longTaskTimer, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    writeMeter(longTaskTimer, metricBatchBuilder, tempBuffer);
  }

  private void writeFunctionCounter(FunctionCounter functionCounter, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    String functionCounterName = functionCounter.getId().getName() + ".count";

    writeAsCounter(
      functionCounter.getId().withName(functionCounterName),
      Math.round(functionCounter.count()),
      metricBatchBuilder,
      tempBuffer
    );
  }

  private void writeFunctionTimer(FunctionTimer functionTimer, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    String functionCounterName = functionTimer.getId().getName() + ".count";
    String functionTotalName = functionTimer.getId().getName() + ".total";

    writeAsCounter(
      functionTimer.getId().withName(functionCounterName),
      Math.round(functionTimer.count()),
      metricBatchBuilder,
      tempBuffer
    );

    writeAsCounter(
      functionTimer.getId().withName(functionTotalName),
      Math.round(functionTimer.totalTime(getBaseTimeUnit())),
      metricBatchBuilder,
      tempBuffer
    );
  }

  private void writeMeter(Meter meter, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    meter.measure().forEach(measurement -> {
      switch(measurement.getStatistic()) {
        case COUNT:
        case TOTAL:
        case TOTAL_TIME:
          String counterName = meter.getId().getName() + "." + measurement.getStatistic().getTagValueRepresentation();

          writeAsCounter(
            meter.getId().withName(counterName),
            Math.round(measurement.getValue()),
            metricBatchBuilder,
            tempBuffer
          );
          break;

        default:
          String measurementName = meter.getId().getName() + "." + measurement.getStatistic().getTagValueRepresentation();
          writeAsGauge(
            meter.getId().withName(measurementName),
            Math.round(measurement.getValue()),
            metricBatchBuilder,
            tempBuffer
          );
      }
    });
  }

  private void writeDistribution(Meter.Id id, MetricBatch.Builder metricBatchBuilder, ByteBuffer tempBuffer) {
    // At this point the entire HDR histogram data is already written on tempBuffer but we need to
    // rewind the buffer and skip the first 40 bytes where the HDR Histogram format includes configuration
    // settings for the histogram.
    int snapshotSize = tempBuffer.flip().position(40).remaining();

    metricBatchBuilder.addMetrics(
      Metric.newBuilder()
        .setName(id.getName())
        .putAllTags(tagListToMap(id.getTags()))
        .setInstrumentType(InstrumentType.HISTOGRAM)
        .setData(ByteString.copyFrom(tempBuffer.array(), 40, snapshotSize))
        .build()
    );
  }

  private Map<String, String> tagListToMap(List<Tag> tags) {
    return tags.stream().collect(Collectors.toMap(Tag::getKey, Tag::getValue));
  }

  @Override
  protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
    return new IntervalTimer(id, clock, defaultHistogramConfig(), pauseDetector, getBaseTimeUnit(), false);
  }

  @Override
  protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
    return new IntervalDistributionSummary(id, clock, distributionStatisticConfig, scale, false);
  }

  @Override
  protected TimeUnit getBaseTimeUnit() {
    return TimeUnit.NANOSECONDS;
  }

  @Override
  protected DistributionStatisticConfig defaultHistogramConfig() {
    return DistributionStatisticConfig.builder()
        .percentilePrecision(2)
        .percentilesHistogram(false)
        .build()
        .merge(DistributionStatisticConfig.DEFAULT);
  }


  /**
   * These values are part of the HDR Histogram internal configuration that we use to translate any given value into
   * an index in the counts array, to match what we have on the Kamon APM side.
   */
  private static final int SubBucketHalfCountMagnitude = 7;
  private static final int SubBucketHalfCount          = 128;
  private static final int UnitMagnitude               = 0;
  private static final int SubBucketCount              = (int) Math.pow(2, SubBucketHalfCountMagnitude + 1);
  private static final int LeadingZeroCountBase        = 64 - UnitMagnitude - SubBucketHalfCountMagnitude - 1;
  private static final long SubBucketMask              = (((long) SubBucketCount) - 1) << UnitMagnitude;

  private int hdrHistogramIndexForValue(long value) {
    int bucketIndex = LeadingZeroCountBase - java.lang.Long.numberOfLeadingZeros(value | SubBucketMask);
    int subBucketIndex = (int) Math.floor(value / Math.pow(2, (bucketIndex + UnitMagnitude)));
    int bucketBaseIndex = (bucketIndex + 1) << SubBucketHalfCountMagnitude;
    int offsetInBucket = subBucketIndex - SubBucketHalfCount;

    return bucketBaseIndex + offsetInBucket;
  }

  private static void putLong(ByteBuffer buffer, long value) {
    value = (value << 1) ^ (value >> 63);
    if (value >>> 7 == 0) {
      buffer.put((byte) value);
    } else {
      buffer.put((byte) ((value & 0x7F) | 0x80));
      if (value >>> 14 == 0) {
        buffer.put((byte) (value >>> 7));
      } else {
        buffer.put((byte) (value >>> 7 | 0x80));
        if (value >>> 21 == 0) {
          buffer.put((byte) (value >>> 14));
        } else {
          buffer.put((byte) (value >>> 14 | 0x80));
          if (value >>> 28 == 0) {
            buffer.put((byte) (value >>> 21));
          } else {
            buffer.put((byte) (value >>> 21 | 0x80));
            if (value >>> 35 == 0) {
              buffer.put((byte) (value >>> 28));
            } else {
              buffer.put((byte) (value >>> 28 | 0x80));
              if (value >>> 42 == 0) {
                buffer.put((byte) (value >>> 35));
              } else {
                buffer.put((byte) (value >>> 35 | 0x80));
                if (value >>> 49 == 0) {
                  buffer.put((byte) (value >>> 42));
                } else {
                  buffer.put((byte) (value >>> 42 | 0x80));
                  if (value >>> 56 == 0) {
                    buffer.put((byte) (value >>> 49));
                  } else {
                    buffer.put((byte) (value >>> 49 | 0x80));
                    buffer.put((byte) (value >>> 56));
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
