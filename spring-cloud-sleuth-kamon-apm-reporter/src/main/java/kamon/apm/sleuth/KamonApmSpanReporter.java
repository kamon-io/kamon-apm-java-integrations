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

package kamon.apm.sleuth;

import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
import kamon.apm.KamonApmConfig;
import kamon.apm.KamonApmMeterRegistry;
import kamon.apm.ingestion.v1.traces.SpanKind;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class KamonApmSpanReporter implements Reporter<Span>, Closeable {
  private final InternalLogger logger = InternalLoggerFactory.getInstance(KamonApmMeterRegistry.class);

  private final ScheduledExecutorService scheduler;
  private final ArrayBlockingQueue<Span> pendingSpans;
  private final KamonApmConfig apmConfig;
  private final HttpSender httpSender;
  private final String kamonApmIngestUrl;

  public KamonApmSpanReporter(KamonApmConfig apmConfig, KamonApmSpanReporterConfig spanReporterConfig) {
    this.apmConfig = apmConfig;

    String cleanBaseUrl = apmConfig.baseUrl().endsWith("/") ? apmConfig.baseUrl() : apmConfig.baseUrl().concat("/");
    this.kamonApmIngestUrl = cleanBaseUrl.concat("v1/tracing/ingest");
    this.pendingSpans = new ArrayBlockingQueue<>(spanReporterConfig.bufferSize());
    this.httpSender = new HttpUrlConnectionSender(Duration.ofSeconds(3), Duration.ofSeconds(10));
    this.scheduler = Executors.newScheduledThreadPool(1, new SpanReporterThreadFactory());
    this.scheduler.scheduleAtFixedRate(this::flush, 5, spanReporterConfig.flushInterval().getSeconds(), TimeUnit.SECONDS);
  }

  @Override
  public void report(Span span) {
    pendingSpans.offer(span);
  }

  @Override
  public void close() throws IOException {
    this.scheduler.shutdown();
  }

  private void flush() {
    try {
      final List<Span> spansToFlush = new LinkedList<>();
      this.pendingSpans.drainTo(spansToFlush);

      if(!spansToFlush.isEmpty()) {
        byte[] spanBatchData = kamon.apm.ingestion.v1.traces.SpanBatch.newBuilder()
          .setServiceName(this.apmConfig.applicationName())
          .setApiKey(this.apmConfig.apiKey())
          .setHost(this.apmConfig.host())
          .setInstance(this.apmConfig.instance())
          .setAgent("micrometer")
          .addAllSpans(convertSpans(spansToFlush))
          .build()
          .toByteArray();

        HttpSender.Response response = httpSender.post(kamonApmIngestUrl)
          .withContent("application/octet-stream", spanBatchData)
          .send();

        if (!response.isSuccessful()) {
          logger.error("Publishing spans to Kamon APM failed with HTTP Status [{}]", response.code());
        }
      }
    } catch (Throwable e) {
      logger.error("Failed to publish spans to Kamon APM", e);
    }

  }

  private List<kamon.apm.ingestion.v1.traces.Span> convertSpans(List<Span> zipkinSpans) {
    return zipkinSpans.stream()
      .map(s -> {
        boolean hasError = s.tags().getOrDefault("error", "false").equals("true");
        String parentId = s.parentId() == null ? "" : s.parentId();

        kamon.apm.ingestion.v1.traces.Span.Builder spanBuilder = kamon.apm.ingestion.v1.traces.Span.newBuilder()
          .setId(s.id())
          .setParentId(parentId)
          .setTraceId(s.traceId())
          .setKind(s.kind() == null ? SpanKind.INTERNAL : SpanKind.valueOf(s.kind().toString()))
          .setOperationName(s.name())
          .setStartMicros(s.timestampAsLong())
          .setEndMicros(s.durationAsLong() + s.timestampAsLong())
          .setHasError(hasError);


        if(!s.tags().isEmpty()) {
          spanBuilder.putAllTags(s.tags());
        }

        return spanBuilder.build();
      })
      .collect(Collectors.toList());
  }



  // There is no need for a sequence on thread names because there will
  // only be one of these threads.
  private static class SpanReporterThreadFactory implements ThreadFactory {

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r);
      thread.setName("kamon-apm-span-reporter-flusher");
      thread.setDaemon(true);
      return thread;
    }
  }

}
