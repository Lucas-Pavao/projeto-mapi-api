package com.projeto.mapi.service.stream.impl;

import com.projeto.mapi.dto.AlertDTO;
import com.projeto.mapi.dto.PredictionSummaryDTO;
import com.projeto.mapi.exception.TooManyRequestsException;
import com.projeto.mapi.service.alert.AlertCreatedEvent;
import com.projeto.mapi.service.flood.PredictionCycleCompletedEvent;
import com.projeto.mapi.service.flood.PredictionQueryService;
import com.projeto.mapi.service.sensor.SensorReadingSavedEvent;
import com.projeto.mapi.service.stream.LiveStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveStreamServiceImpl implements LiveStreamService {

    static final int MAX_CONNECTIONS_PER_USER = 3;
    static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;
    static final int BUFFER_SIZE = 200;

    private final PredictionQueryService predictionQuery;
    private final Clock clock;

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Deque<Buffered> buffer = new ArrayDeque<>();

    private record Subscriber(String username, boolean receivesAlerts, SseEmitter emitter) {
    }

    private record Buffered(long id, String name, Object data, boolean alertOnly) {
    }

    @Override
    public SseEmitter subscribe(String username, boolean receivesAlerts, String lastEventId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Subscriber sub = new Subscriber(username, receivesAlerts, emitter);
        synchronized (subscribers) {
            long open = subscribers.stream().filter(s -> s.username().equals(username)).count();
            if (open >= MAX_CONNECTIONS_PER_USER) {
                throw new TooManyRequestsException("Limite de " + MAX_CONNECTIONS_PER_USER + " conexões por usuário", 30);
            }
            subscribers.add(sub);
        }
        Runnable remove = () -> subscribers.remove(sub);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(e -> remove.run());
        replay(sub, lastEventId);
        return emitter;
    }

    @Override
    public void disconnect(String username) {
        for (Subscriber s : List.copyOf(subscribers)) {
            if (!s.username().equals(username)) continue;
            subscribers.remove(s);
            try {
                s.emitter().complete();
            } catch (Exception ignored) {
                // já encerrado
            }
        }
    }

    @Override
    public int activeConnections() {
        return subscribers.size();
    }

    private void replay(Subscriber sub, String lastEventId) {
        if (lastEventId == null) return;
        long last;
        try {
            last = Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException e) {
            return;
        }
        List<Buffered> pending;
        synchronized (buffer) {
            pending = buffer.stream().filter(b -> b.id() > last).toList();
        }
        for (Buffered b : pending) {
            if (b.alertOnly() && !sub.receivesAlerts()) continue;
            send(sub, b);
        }
    }

    // ---- fontes de evento ----

    /** Depois do avaliador de alertas (ordem mais alta): publica previsões e a contagem por nível. */
    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onCycleCompleted(PredictionCycleCompletedEvent event) {
        if (subscribers.isEmpty()) return;
        try {
            for (PredictionSummaryDTO p : predictionQuery.latestPerActivePoint()) {
                broadcast("prediction", p, false);
            }
            broadcast("risk-summary", predictionQuery.riskSummary(), false);
        } catch (Exception e) {
            log.warn("Falha ao publicar o fim do ciclo no stream: {}", e.getMessage());
        }
    }

    @EventListener
    public void onAlert(AlertCreatedEvent event) {
        AlertDTO alert = event.alert();
        broadcast("alert", alert, true);
    }

    @EventListener
    public void onSensor(SensorReadingSavedEvent event) {
        if (subscribers.isEmpty()) return;
        broadcast("sensor", event.reading(), false);
    }

    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        for (Subscriber s : List.copyOf(subscribers)) {
            send(s, new Buffered(0, "heartbeat", java.util.Map.of("at", clock.instant().toString()), false));
        }
    }

    private void broadcast(String name, Object data, boolean alertOnly) {
        long id = sequence.incrementAndGet();
        Buffered b = new Buffered(id, name, data, alertOnly);
        synchronized (buffer) {
            buffer.addLast(b);
            while (buffer.size() > BUFFER_SIZE) buffer.removeFirst();
        }
        for (Subscriber s : new ArrayList<>(subscribers)) {
            if (alertOnly && !s.receivesAlerts()) continue;
            send(s, b);
        }
    }

    private void send(Subscriber s, Buffered b) {
        try {
            SseEmitter.SseEventBuilder ev = SseEmitter.event().name(b.name()).data(b.data(), MediaType.APPLICATION_JSON);
            if (b.id() > 0) ev.id(String.valueOf(b.id()));
            s.emitter().send(ev);
        } catch (IOException | IllegalStateException e) {
            subscribers.remove(s);
            try {
                s.emitter().complete();
            } catch (Exception ignored) {
                // já encerrado
            }
        }
    }
}
