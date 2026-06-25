package com.example.monitoring;

import com.example.repository.SalesDataRepository;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "batch.features.metrics-export", havingValue = "true", matchIfMissing = true)
public class MetricsConfiguration {

    private final MeterRegistry meterRegistry;
    private final SalesDataRepository salesDataRepository;

    public MetricsConfiguration(MeterRegistry meterRegistry, SalesDataRepository salesDataRepository) {
        this.meterRegistry = meterRegistry;
        this.salesDataRepository = salesDataRepository;
    }

    @Bean
    public TimedAspect timedAspect() {
        return new TimedAspect(meterRegistry);
    }

    @PostConstruct
    public void registerCustomGauges() {
        meterRegistry.gauge("batch.records.unprocessed", salesDataRepository,
                repo -> repo.countUnprocessed().doubleValue());
    }
}
