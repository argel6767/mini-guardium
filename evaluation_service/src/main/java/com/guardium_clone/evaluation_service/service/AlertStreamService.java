package com.guardium_clone.evaluation_service.service;

import com.guardium_clone.evaluation_service.api.AlertBatchResponse;
import com.guardium_clone.evaluation_service.api.AlertMapper;
import com.guardium_clone.evaluation_service.api.AlertRateSnapshotResponse;
import com.guardium_clone.evaluation_service.api.AlertResponse;
import com.guardium_clone.evaluation_service.events.AlertCreatedEvent;
import com.guardium_clone.evaluation_service.model.Alert;
import com.guardium_clone.evaluation_service.model.AlertSeverity;
import com.guardium_clone.evaluation_service.repository.AlertRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AlertStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertStreamService.class);
    private static final long STREAM_TIMEOUT_MILLIS = 0L;
    private static final int MAX_BATCH_SIZE = 100;
    private static final Duration RATE_WINDOW = Duration.ofSeconds(60);
    private static final Duration RATE_INTERVAL = Duration.ofSeconds(5);

    private final AlertRepository alertRepository;
    private final AlertMapper alertMapper;
    private final Clock clock;
    private final Set<SseEmitter> severityEmitters = ConcurrentHashMap.newKeySet();
    private final Set<SseEmitter> batchEmitters = ConcurrentHashMap.newKeySet();
    private final Set<SseEmitter> rateEmitters = ConcurrentHashMap.newKeySet();
    private final Queue<AlertResponse> pendingBatchAlerts = new ConcurrentLinkedQueue<>();
    private final List<RateSample> rateSamples = new ArrayList<>();

    @Autowired
    public AlertStreamService(AlertRepository alertRepository, AlertMapper alertMapper) {
        this(alertRepository, alertMapper, Clock.systemUTC());
    }

    AlertStreamService(AlertRepository alertRepository, AlertMapper alertMapper, Clock clock) {
        this.alertRepository = alertRepository;
        this.alertMapper = alertMapper;
        this.clock = clock;
    }

    public SseEmitter openSeverityStream() {
        return registerEmitter(severityEmitters);
    }

    public SseEmitter openBatchStream() {
        return registerEmitter(batchEmitters);
    }

    public SseEmitter openRateStream() {
        SseEmitter emitter = registerEmitter(rateEmitters);
        send(emitter, "alerts.rate", rateSnapshot());
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAlertCreated(AlertCreatedEvent event) {
        alertRepository.findByIdWithDetails(event.alertId()).ifPresent(alert -> {
            AlertResponse alertResponse = alertMapper.toResponse(alert);
            sendToAll(severityEmitters, "alert.severity", alertMapper.toSeverityEvent(alert));
            pendingBatchAlerts.add(alertResponse);
            recordRateSample(alert);
        });
    }

    @Scheduled(fixedRateString = "${alerts.stream.batch-flush-rate:PT2S}")
    void flushAlertBatches() {
        List<AlertResponse> batch = new ArrayList<>(MAX_BATCH_SIZE);
        while (batch.size() < MAX_BATCH_SIZE) {
            AlertResponse alert = pendingBatchAlerts.poll();
            if (alert == null) {
                break;
            }
            batch.add(alert);
        }

        if (!batch.isEmpty()) {
            sendToAll(batchEmitters, "alerts.batch", new AlertBatchResponse(batch, batch.size(), Instant.now(clock)));
        }
    }

    @Scheduled(fixedRateString = "${alerts.stream.rate-flush-rate:PT5S}")
    void flushRateSnapshots() {
        sendToAll(rateEmitters, "alerts.rate", rateSnapshot());
    }

    @Scheduled(fixedRateString = "${alerts.stream.heartbeat-rate:PT15S}")
    void sendHeartbeats() {
        Instant now = Instant.now(clock);
        sendToAll(severityEmitters, "heartbeat", now);
        sendToAll(batchEmitters, "heartbeat", now);
        sendToAll(rateEmitters, "heartbeat", now);
    }

    AlertRateSnapshotResponse rateSnapshot() {
        Instant now = Instant.now(clock);
        List<RateSample> samples;
        synchronized (rateSamples) {
            pruneRateSamples(now);
            samples = List.copyOf(rateSamples);
        }

        Map<AlertSeverity, Double> bySeverity = new EnumMap<>(AlertSeverity.class);
        for (AlertSeverity severity : AlertSeverity.values()) {
            bySeverity.put(severity, 0.0);
        }
        Map<String, Double> byRule = new HashMap<>();
        double multiplier = 60.0 / RATE_WINDOW.toSeconds();

        for (RateSample sample : samples) {
            bySeverity.compute(sample.severity(), (key, value) -> value + multiplier);
            byRule.merge(sample.ruleName(), multiplier, Double::sum);
        }

        return new AlertRateSnapshotResponse(
                now,
                RATE_WINDOW.toSeconds(),
                RATE_INTERVAL.toSeconds(),
                samples.size() * multiplier,
                bySeverity,
                byRule
        );
    }

    private SseEmitter registerEmitter(Set<SseEmitter> emitters) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
        return emitter;
    }

    private void recordRateSample(Alert alert) {
        synchronized (rateSamples) {
            Instant now = Instant.now(clock);
            rateSamples.add(new RateSample(now, alert.getSeverity(), alert.getRuleName()));
            pruneRateSamples(now);
        }
    }

    private void pruneRateSamples(Instant now) {
        Instant cutoff = now.minus(RATE_WINDOW);
        Iterator<RateSample> iterator = rateSamples.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().createdAt().isBefore(cutoff)) {
                iterator.remove();
            }
        }
    }

    private void sendToAll(Set<SseEmitter> emitters, String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            send(emitter, eventName, payload);
        }
    }

    private void send(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException | IllegalStateException ex) {
            LOGGER.debug("Removing closed alert SSE emitter after send failure", ex);
            severityEmitters.remove(emitter);
            batchEmitters.remove(emitter);
            rateEmitters.remove(emitter);
        }
    }

    private record RateSample(Instant createdAt, AlertSeverity severity, String ruleName) {
    }
}

