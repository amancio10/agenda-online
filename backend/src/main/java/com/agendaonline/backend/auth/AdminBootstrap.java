package com.agendaonline.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro administrador a partir de ADMIN_EMAIL e ADMIN_PASSWORD, se ainda não existir
 * nenhum. Depois de criado, essas variáveis podem ser apagadas.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminUserRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(AdminUserRepository admins, PasswordEncoder passwordEncoder,
            @Value("${app.admin.email:}") String email, @Value("${app.admin.password:}") String password) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank() || admins.count() > 0) {
            return;
        }
        if (password.length() < 8) {
            throw new IllegalStateException("ADMIN_PASSWORD precisa ter pelo menos 8 caracteres.");
        }
        admins.save(new AdminUser(email.trim().toLowerCase(), passwordEncoder.encode(password)));
        log.info("Administrador inicial criado: {}", email.trim().toLowerCase());
    }
}
