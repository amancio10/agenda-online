package com.agendaonline.backend.booking;

import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Avisa o administrador por e-mail quando um visitante marca uma reunião.
 *
 * <p>Envia pela API HTTP do Brevo quando {@code BREVO_API_KEY} está definida (funciona em
 * hospedagens que bloqueiam SMTP); senão, usa o SMTP configurado em {@code spring.mail.*}.
 *
 * <p>Roda em segundo plano ({@code @Async}): o visitante não espera o envio, e uma falha
 * só é registrada no log — o agendamento já está salvo.
 * Fica desligado enquanto {@code app.notify.to} ou {@code app.notify.from} estiverem vazios.
 */
@Component
public class BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final BrevoMailClient brevo;
    private final String to;
    private final String from;

    public BookingNotifier(ObjectProvider<JavaMailSender> mailSender, BrevoMailClient brevo,
            @Value("${app.notify.to:}") String to,
            @Value("${app.notify.from:}") String from) {
        this.mailSender = mailSender;
        this.brevo = brevo;
        this.to = to.trim();
        this.from = from.trim();
    }

    public boolean isEnabled() {
        return !to.isEmpty() && !from.isEmpty() && (brevo.isConfigured() || mailSender.getIfAvailable() != null);
    }

    @Async
    public void bookingCreated(Booking booking) {
        if (!isEnabled()) {
            return;
        }
        String via = brevo.isConfigured() ? "Brevo" : "SMTP";
        try {
            SimpleMailMessage msg = message(booking);
            if (brevo.isConfigured()) {
                brevo.send(msg);
            } else {
                mailSender.getObject().send(msg);
            }
            log.info("Aviso de novo agendamento enviado via {} ({} {}).", via, booking.getDate(), booking.getTime());
        } catch (RuntimeException e) { // MailException (SMTP) ou RestClientException (Brevo)
            log.warn("Não foi possível enviar o aviso de novo agendamento: {}", e.getMessage());
        }
    }

    SimpleMailMessage message(Booking booking) {
        String date = booking.getDate().format(DATE);
        String time = booking.getTime().format(TIME);
        String topic = booking.getTopic().isBlank() ? "(não informado)" : booking.getTopic();

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        // "Responder" no seu e-mail já vai direto para o visitante.
        msg.setReplyTo(booking.getEmail());
        msg.setSubject("Nova reunião: " + date + " às " + time + " — " + oneLine(booking.getName()));
        msg.setText("""
                Uma nova reunião foi agendada.

                Data:    %s
                Horário: %s
                Nome:    %s
                E-mail:  %s
                Assunto: %s
                """.formatted(date, time, booking.getName(), booking.getEmail(), topic));
        return msg;
    }

    /** Evita quebras de linha vindas do visitante no cabeçalho do e-mail. */
    private static String oneLine(String s) {
        return s.replaceAll("[\\r\\n]+", " ").strip();
    }
}
