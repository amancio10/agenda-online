package com.agendaonline.backend.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class BookingNotifierTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final Booking booking = new Booking(LocalDate.of(2030, 1, 7), LocalTime.of(9, 0),
            "Ana\r\nBcc: x@evil.com", "ana@example.com", "");

    private BookingNotifier notifier(String to, String from) {
        var beans = new StaticListableBeanFactory();
        beans.addBean("mailSender", sender);
        return new BookingNotifier(beans.getBeanProvider(JavaMailSender.class), to, from);
    }

    @Test
    void sendsTheBookingToTheAdmin() {
        notifier("admin@example.com", "robo@example.com").bookingCreated(booking);

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
        notifier("", "robo@example.com").bookingCreated(booking);
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void mailFailureDoesNotPropagate() {
        doThrow(new MailSendException("smtp fora")).when(sender).send(any(SimpleMailMessage.class));
        notifier("admin@example.com", "robo@example.com").bookingCreated(booking);
    }
}
