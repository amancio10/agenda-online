package com.agendaonline.backend.booking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envia e-mails pela API HTTP do Brevo (https://developers.brevo.com).
 *
 * <p>Alternativa ao SMTP para hospedagens que bloqueiam as portas de e-mail
 * (ex.: plano gratuito do Render). Usa HTTPS comum, na porta 443.
 * Fica desligado enquanto {@code BREVO_API_KEY} estiver vazia.
 */
@Component
public class BrevoMailClient {

    private final String apiKey;
    private final RestClient http;

    @Autowired
    public BrevoMailClient(@Value("${app.notify.brevo-api-key:}") String apiKey,
            @Value("${app.notify.brevo-url:https://api.brevo.com}") String baseUrl) {
        this(apiKey, RestClient.builder().baseUrl(baseUrl).requestFactory(timeouts()));
    }

    /** Usado nos testes, para trocar o servidor real por um simulado. */
    BrevoMailClient(String apiKey, RestClient.Builder builder) {
        this.apiKey = apiKey.trim();
        this.http = builder.build();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public void send(SimpleMailMessage msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", Map.of("email", msg.getFrom(), "name", "Agenda Online"));
        body.put("to", List.of(msg.getTo()).stream().map(email -> Map.of("email", email)).toList());
        if (msg.getReplyTo() != null) {
            body.put("replyTo", Map.of("email", msg.getReplyTo()));
        }
        body.put("subject", msg.getSubject());
        body.put("textContent", msg.getText());

        // Uma resposta de erro (4xx/5xx) vira exceção e é registrada no log pelo BookingNotifier.
        http.post()
                .uri("/v3/smtp/email")
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(10_000);
        return factory;
    }
}
