package com.projeto.mapi.config;

import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import com.projeto.mapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria o primeiro usuário ADMIN a partir de variáveis de ambiente, se ele ainda não existir.
 * Sem isso, não há nenhuma forma de acessar os endpoints protegidos por role ADMIN (ex:
 * /api/admin/ingestion/**) numa instalação nova, já que o cadastro público (/api/auth/register)
 * sempre cria usuários com Role.USER.
 *
 * É deliberadamente idempotente e conservador: só cria o usuário se ADMIN_BOOTSTRAP_USERNAME e
 * ADMIN_BOOTSTRAP_PASSWORD estiverem definidas e esse usuário ainda não existir. Nunca promove
 * ou altera um usuário já existente automaticamente a cada restart, pra não sobrescrever uma
 * mudança de segurança feita manualmente (ex: alguém rebaixou uma conta comprometida).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class AdminBootstrapSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_BOOTSTRAP_USERNAME:}")
    private String bootstrapUsername;

    @Value("${ADMIN_BOOTSTRAP_PASSWORD:}")
    private String bootstrapPassword;

    @Override
    public void run(String... args) {
        if (bootstrapUsername == null || bootstrapUsername.isBlank()
                || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            log.debug("ADMIN_BOOTSTRAP_USERNAME/PASSWORD não definidas — nenhum admin será criado automaticamente.");
            return;
        }

        if (userRepository.findByUsername(bootstrapUsername).isPresent()) {
            log.info("Usuário admin de bootstrap '{}' já existe — nada a fazer.", bootstrapUsername);
            return;
        }

        User admin = User.builder()
                .username(bootstrapUsername)
                .password(passwordEncoder.encode(bootstrapPassword))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);
        log.info("[✓] Usuário ADMIN de bootstrap '{}' criado com sucesso.", bootstrapUsername);
    }
}
