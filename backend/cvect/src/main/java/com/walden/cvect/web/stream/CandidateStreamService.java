package com.walden.cvect.web.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 事件推送服务
 */
@Service
public class CandidateStreamService {

    private static final Logger log = LoggerFactory.getLogger(CandidateStreamService.class);
    private static final long DEFAULT_TIMEOUT_MS = 10 * 60 * 1000L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final long emitterTimeoutMs;

    public CandidateStreamService(@Value("${app.sse.timeout-ms:600000}") long emitterTimeoutMs) {
        this.emitterTimeoutMs = emitterTimeoutMs > 0 ? emitterTimeoutMs : DEFAULT_TIMEOUT_MS;
    }

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("ping")
                    .data("ok"));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 推送候选人事件给所有订阅者
     */
    public void publish(CandidateStreamEvent event) {
        if (event == null) {
            return;
        }

        sendEvent("candidate", event);
    }

    public void publishVectorStatus(VectorStatusStreamEvent event) {
        if (event == null) {
            return;
        }
        sendEvent("vector", event);
    }

    public int activeEmitterCount() {
        return emitters.size();
    }

    /**
     * SSE 心跳，防止代理/浏览器断开连接
     */
    @Scheduled(fixedRate = 20000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        sendEvent("ping", "ok");
    }

    private void sendEvent(String name, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(name)
                        .data(data));
            } catch (IOException e) {
                log.debug("Failed to send SSE event, removing emitter", e);
                emitters.remove(emitter);
                emitter.completeWithError(e);
            }
        }
    }
}
