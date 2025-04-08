package viettel.dac.prototype.execution.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration class for metrics collection and monitoring.
 */
@Configuration
public class MetricsConfig {

    @Value("${spring.application.name:execution-engine}")
    private String applicationName;

    @Value("${execution.metrics.percentiles:0.5,0.95,0.99}")
    private String configuredPercentiles;

    @Value("${execution.metrics.sla:100,500,1000,5000}")
    private String configuredSla;

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            registry.config()
                    .commonTags("application", applicationName)
                    .meterFilter(executionTimingConfiguration());

            // Add hostname tag
            String hostname = System.getenv("HOSTNAME");
            if (hostname != null && !hostname.isEmpty()) {
                registry.config().commonTags("host", hostname);
            }
        };
    }

    private MeterFilter executionTimingConfiguration() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().startsWith("execution.")) {
                    return DistributionStatisticConfig.builder()
                            .percentiles(parsePercentiles(configuredPercentiles))
                            .serviceLevelObjectives(parseSlaAsDoubles(configuredSla))
                            .expiry(Duration.ofMinutes(5))
                            .bufferLength(3600)
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    private double[] parsePercentiles(String percentileString) {
        if (percentileString == null || percentileString.isEmpty()) {
            return new double[0];
        }

        String[] parts = percentileString.split(",");
        double[] result = new double[parts.length];

        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }

        return result;
    }

    private double[] parseSlaAsDoubles(String slaString) {
        if (slaString == null || slaString.isEmpty()) {
            return new double[0];
        }

        String[] parts = slaString.split(",");
        double[] result = new double[parts.length];

        for (int i = 0; i < parts.length; i++) {
            result[i] = Double.parseDouble(parts[i].trim());
        }

        return result;
    }
}
