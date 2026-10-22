package com.projeto.mapi.security;

import com.projeto.mapi.model.User;
import com.projeto.mapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache curto (30 s) do estado de acesso de cada usuário: tokenVersion e ativação. O filtro JWT o consulta a cada
 * requisição para recusar token revogado (ADR 0010, emenda 2) sem ir ao banco toda vez. Quem muda perfil ou ativação
 * pela API chama {@link #evict}, então nesta instância a revogação vale na próxima requisição; em outra instância,
 * valeria em até 30 s.
 */
@Component
@RequiredArgsConstructor
public class UserAccessCache {

    public static final Duration TTL = Duration.ofSeconds(30);

    private record Entry(User user, Instant loadedAt) {
    }

    private final UserRepository userRepository;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /** Estado atual do usuário (cópia solta da sessão do Hibernate), ou vazio se ele não existe mais. */
    public Optional<User> find(String username) {
        Instant now = clock.instant();
        Entry hit = entries.get(username);
        if (hit != null && Duration.between(hit.loadedAt(), now).compareTo(TTL) < 0) return Optional.of(hit.user());
        Optional<User> loaded = userRepository.findByUsername(username).map(UserAccessCache::snapshot);
        loaded.ifPresentOrElse(u -> entries.put(username, new Entry(u, now)), () -> entries.remove(username));
        return loaded;
    }

    public void evict(String username) {
        entries.remove(username);
    }

    private static User snapshot(User u) {
        return User.builder().id(u.getId()).username(u.getUsername()).password(u.getPassword()).role(u.getRole())
                .active(u.isActive()).email(u.getEmail()).name(u.getName()).tokenVersion(u.getTokenVersion())
                .lastLoginAt(u.getLastLoginAt()).build();
    }
}
