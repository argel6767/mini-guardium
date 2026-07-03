package com.guardium_clone.traffic_simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class TrafficSimulationServiceSelectionTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ServiceSelectionConfiguration.class);

    @Test
    void sequentialServiceIsUsedByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TrafficSimulationService.class);
            assertThat(context.getBean(TrafficSimulationService.class))
                    .isInstanceOf(SequentialTrafficSimulationService.class);
        });
    }


    @Test
    void sequentialServiceIsUsedWhenConcurrentModeIsExplicitlyDisabled() {
        contextRunner
                .withPropertyValues("traffic-simulator.concurrent=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(TrafficSimulationService.class);
                    assertThat(context.getBean(TrafficSimulationService.class))
                            .isInstanceOf(SequentialTrafficSimulationService.class);
                });
    }

    @Test
    void concurrentServiceIsUsedWhenConfigured() {
        contextRunner
                .withPropertyValues("traffic-simulator.concurrent=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(TrafficSimulationService.class);
                    assertThat(context.getBean(TrafficSimulationService.class))
                            .isInstanceOf(ConcurrentTrafficSimulationService.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            SequentialTrafficSimulationService.class,
            ConcurrentTrafficSimulationService.class
    })
    static class ServiceSelectionConfiguration {

        @Bean
        TrafficEventSender trafficEventSender() {
            return mock(TrafficEventSender.class);
        }
    }
}
