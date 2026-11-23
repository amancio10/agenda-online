package com.agendaonline.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BookingNotifierTest {

    private static final String BREVO_URL = "https://api.brevo.test";

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final Booking booking = new Booking(LocalDate.of(2030, 1, 7), LocalTime.of(9, 0),
            "Ana\r\nBcc: x@evil.com", "ana@example.com", "");

    private BookingNotifier notifier(BrevoMailClient brevo, String to) {
        var beans = new StaticListableBeanFactory();
        beans.addBean("mailSender", sender);
        return new BookingNotifier(beans.getBeanProvider(JavaMailSender.class), brevo, to, "robo@example.com");
    }

    private BookingNotifier smtpNotifier(String to) {
        return notifier(new BrevoMailClient("", RestClient.builder()), to);
    }

    // ---------- SMTP ----------

    @Test
    void sendsTheBookingToTheAdminBySmtp() {
        smtpNotifier("admin@example.com").bookingCreated(booking);

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("admin@example.com");
        assertThat(msg.getFrom()).isEqualTo("robo@example.com");
        assertThat(msg.getReplyTo()).isEqualTo("ana@example.com");
        assertThat(msg.getSubject()).isEqualTo("Nova reunião: 07/01/2030 às 09:00 — Ana Bcc: x@evil.com");
        assertThat(msg.getText()).contains("09:00", "ana@example.com", "(não informado)");
    }

    @Test
    void staysOffWithoutRecipient() {
        smtpNotifier("").bookingCreated(booking);
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void smtpFailureDoesNotPropagate() {
        doThrow(new MailSendException("smtp fora")).when(sender).send(any(SimpleMailMessage.class));
        smtpNotifier("admin@example.com").bookingCreated(booking);
    }

    // ---------- Brevo ----------

    @Test
    void sendsThroughBrevoWhenApiKeyIsSet() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BREVO_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BREVO_URL + "/v3/smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "chave-teste"))
                .andExpect(content().json("""
                        {"sender": {"email": "robo@example.com", "name": "Agenda Online"},
                         "to": [{"email": "admin@example.com"}],
                         "replyTo": {"email": "ana@example.com"},
                         "subject": "Nova reunião: 07/01/2030 às 09:00 — Ana Bcc: x@evil.com"}
                        """))
                .andRespond(withSuccess("{\"messageId\":\"1\"}", MediaType.APPLICATION_JSON));

        notifier(new BrevoMailClient("chave-teste", builder), "admin@example.com").bookingCreated(booking);

        server.verify();
        verify(sender, never()).send(any(SimpleMailMessage.class)); // não usou o SMTP
    }

    @Test
    void brevoErrorDoesNotPropagate() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BREVO_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BREVO_URL + "/v3/smtp/email")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        notifier(new BrevoMailClient("chave-errada", builder), "admin@example.com").bookingCreated(booking);

        server.verify();
    }
}
