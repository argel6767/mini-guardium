package com.guardium_clone.traffic_simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConcurrentTrafficSimulationServiceTests {

    @Test
    void sendEventsUsesConcurrentVirtualThreadTasks() {
        TrafficEventSender sender = mock(TrafficEventSender.class);
        CountDownLatch started = new CountDownLatch(3);
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger maxActiveTasks = new AtomicInteger();

        doAnswer(invocation -> {
            int active = activeTasks.incrementAndGet();
            maxActiveTasks.updateAndGet(current -> Math.max(current, active));
            started.countDown();
            started.await(1, TimeUnit.SECONDS);
            Thread.sleep(Duration.ofMillis(20));
            activeTasks.decrementAndGet();
            return null;
        }).when(sender).sendEvent();

        ConcurrentTrafficSimulationService service = new ConcurrentTrafficSimulationService(sender);

        service.sendEvents(3);

        verify(sender, times(3)).sendEvent();
        assertThat(maxActiveTasks.get()).isGreaterThan(1);
    }

    @Test
    void sendEventsDoesNotPropagateUnexpectedTaskFailures() {
        TrafficEventSender sender = mock(TrafficEventSender.class);
        doThrow(new IllegalStateException("unexpected failure"))
                .doNothing()
                .when(sender).sendEvent();
        ConcurrentTrafficSimulationService service = new ConcurrentTrafficSimulationService(sender);

        assertThatNoException().isThrownBy(() -> service.sendEvents(2));

        verify(sender, times(2)).sendEvent();
    }

    @Test
    void sendEventsRestoresInterruptedStatusWhileWaitingForTasks() {
        TrafficEventSender sender = mock(TrafficEventSender.class);
        ConcurrentTrafficSimulationService service = new ConcurrentTrafficSimulationService(sender);

        Thread.currentThread().interrupt();
        try {
            service.sendEvents(1);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
