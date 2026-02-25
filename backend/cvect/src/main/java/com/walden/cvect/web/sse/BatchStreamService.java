package com.walden.cvect.web.sse;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class BatchStreamService {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID batchId) {
        SseEmitter emitter = new SseEmitter(0L);
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
