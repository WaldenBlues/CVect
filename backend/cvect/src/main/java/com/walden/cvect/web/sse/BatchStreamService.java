package com.walden.cvect.web.sse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class BatchStreamService {

    private static final long DEFAULT_TIMEOUT_MS = 10 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final long emitterTimeoutMs;

    public BatchStreamService(@Value("${app.sse.timeout-ms:600000}") long emitterTimeoutMs) {
        this.emitterTimeoutMs = emitterTimeoutMs > 0 ? emitterTimeoutMs : DEFAULT_TIMEOUT_MS;
    }

    public SseEmitter subscribe(UUID batchId) {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
        emitters.computeIfAbsent(batchId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(batchId, emitter));
        emitter.onTimeout(() -> remove(batchId, emitter));
        emitter.onError(ex -> remove(batchId, emitter));

        return emitter;
    }

    public void publish(UUID batchId, BatchStreamEvent event) {
        List<SseEmitter> list = emitters.get(batchId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("batch")
                        .data(event));
            } catch (IOException e) {
                remove(batchId, emitter);
                emitter.completeWithError(e);
            }
        }
    }

    public int activeEmitterCount() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    @Scheduled(fixedRate = 20000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, List<SseEmitter>> entry : emitters.entrySet()) {
            UUID batchId = entry.getKey();
            List<SseEmitter> list = entry.getValue();
            if (list == null || list.isEmpty()) {
                continue;
            }
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("ping")
                            .data("ok"));
                } catch (IOException e) {
                    remove(batchId, emitter);
                    emitter.completeWithError(e);
                }
            }
        }
    }

    private void remove(UUID batchId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(batchId);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(batchId);
        }
    }
}
