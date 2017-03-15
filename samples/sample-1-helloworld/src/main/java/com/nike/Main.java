package com.nike;

import com.nike.helloworld.HelloWorldEndpoint;
import com.nike.helloworld.SignalFxTestEndpoint;
import com.nike.internal.util.Pair;
import com.nike.riposte.metrics.MetricsListener;
import com.nike.riposte.metrics.codahale.CodahaleMetricsCollector;
import com.nike.riposte.metrics.codahale.CodahaleMetricsEngine;
import com.nike.riposte.metrics.codahale.CodahaleMetricsListener;
import com.nike.riposte.metrics.codahale.CodahaleMetricsListener.MetricNamingStrategy;
import com.nike.riposte.metrics.codahale.EndpointMetricsHandler;
import com.nike.riposte.metrics.codahale.contrib.DefaultSLF4jReporterFactory;
import com.nike.riposte.metrics.codahale.contrib.SignalFxReporterFactory;
import com.nike.riposte.metrics.codahale.impl.SignalFxEndpointMetricsHandler;
import com.nike.riposte.metrics.codahale.impl.SignalFxEndpointMetricsHandler.MetricDimensionConfigurator;
import com.nike.riposte.metrics.codahale.impl.SignalFxEndpointMetricsHandler.RollingWindowTimerBuilder;
import com.nike.riposte.server.Server;
import com.nike.riposte.server.config.ServerConfig;
import com.nike.riposte.server.http.Endpoint;
import com.nike.riposte.server.logging.AccessLogger;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.signalfx.codahale.reporter.SignalFxReporter;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static com.nike.riposte.metrics.codahale.impl.SignalFxEndpointMetricsHandler.DEFAULT_REQUEST_LATENCY_TIMER_DIMENSION_CONFIGURATOR;

/**
 * Typically trivial sample to demonstrate the use of the Riposte core framework. After this application starts up
 * you can hit http://localhost:8080/ to receive a "Hello, world" response from {@link HelloWorldEndpoint}.
 */
public class Main {

    public static class AppServerConfig implements ServerConfig {
        private final Collection<Endpoint<?>> endpoints =
            Arrays.asList(new HelloWorldEndpoint(), new SignalFxTestEndpoint());
        private final AccessLogger accessLogger = new AccessLogger();

        private final MetricsListener metricsListener;

        public AppServerConfig() {
            MetricRegistry metricRegistry = new MetricRegistry();
            CodahaleMetricsCollector cmc = new CodahaleMetricsCollector(metricRegistry);
            SignalFxReporterFactory signalFxReporterFactory = new SignalFxReporterFactory(
                "INSERT_API_KEY_HERE",
                (builder) -> builder
                    .addUniqueDimension("host", "localhost")
                    .addUniqueDimension("app", "nic_test_signalfx5")
                    .addUniqueDimension("framework", "riposte")
                    .setDetailsToAdd(SignalFxReporter.MetricDetails.ALL),
                Pair.of(10L, TimeUnit.SECONDS)
            );
            CodahaleMetricsEngine metricsEngine = new CodahaleMetricsEngine(
                cmc,
                Arrays.asList(signalFxReporterFactory, new DefaultSLF4jReporterFactory() {
                    @Override
                    public Long getInterval() {
                        return 10L;
                    }

                    @Override
                    public TimeUnit getTimeUnit() {
                        return TimeUnit.SECONDS;
                    }
                })
            );
            EndpointMetricsHandler endpointMetricsHandler = new SignalFxEndpointMetricsHandler(
                signalFxReporterFactory.getReporter(metricRegistry).getMetricMetadata(),
                metricRegistry,
                new RollingWindowTimerBuilder(signalFxReporterFactory.getInterval(), signalFxReporterFactory.getTimeUnit()),
                DEFAULT_REQUEST_LATENCY_TIMER_DIMENSION_CONFIGURATOR.chainedWith(customMetricConfigurator())
            );
            this.metricsListener = CodahaleMetricsListener.newBuilder(cmc)
                                                          .withEndpointMetricsHandler(endpointMetricsHandler)
                                                          .withServerStatsMetricNamingStrategy(MetricNamingStrategy.defaultNoPrefixImpl())
                                                          .withServerConfigMetricNamingStrategy(MetricNamingStrategy.defaultNoPrefixImpl())
                                                          .withRequestAndResponseSizeHistogramSupplier(
                                                              () -> new Histogram(new SlidingTimeWindowReservoir(signalFxReporterFactory.getInterval(), signalFxReporterFactory.getTimeUnit()))
                                                          )
                                                          .build();
            metricsEngine.start();
        }

        @Override
        public Collection<Endpoint<?>> appEndpoints() {
            return endpoints;
        }

        @Override
        public AccessLogger accessLogger() {
            return accessLogger;
        }

        @Override
        public MetricsListener metricsListener() {
            return metricsListener;
        }
    }

    protected static <T extends Metric> MetricDimensionConfigurator<T> customMetricConfigurator() {
        return (rawBuilder, a, b, c, d, e, f, g, h, i, matchingPathTemplate) -> {
            String downstreamSystem = (matchingPathTemplate != null && matchingPathTemplate.startsWith("/hello/"))
                                      ? "FooSystem"
                                      : "BarSystem";
            return rawBuilder.withDimension("downstream_system", downstreamSystem);
        };
    }

    public static void main(String[] args) throws Exception {
        Server server = new Server(new AppServerConfig());
        server.startup();
    }
}
