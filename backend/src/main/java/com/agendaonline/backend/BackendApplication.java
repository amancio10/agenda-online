package com.agendaonline.backend;

import com.agendaonline.backend.config.DotEnv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // o aviso por e-mail é enviado em segundo plano
public class BackendApplication {

	public static void main(String[] args) {
		// Lê backend/.env.local (desenvolvimento) e converte a DATABASE_URL do Neon para JDBC.
		DotEnv.load();
		SpringApplication.run(BackendApplication.class, args);
	}

}
