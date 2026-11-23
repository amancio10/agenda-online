package com.agendaonline.backend.booking;

import com.agendaonline.backend.booking.BookingDtos.NewBookingRequest;
import com.agendaonline.backend.booking.BookingDtos.BookingResponse;
import com.agendaonline.backend.booking.BookingDtos.Status;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository repository;
    private final BookingNotifier notifier;
    private final ZoneId zone;
    private final Duration meetingLength;

    public BookingService(BookingRepository repository, BookingNotifier notifier,
            @Value("${app.timezone}") String timezone,
            @Value("${app.meeting-minutes:30}") int meetingMinutes) {
        this.repository = repository;
        this.notifier = notifier;
        this.zone = ZoneId.of(timezone);
        this.meetingLength = Duration.ofMinutes(meetingMinutes);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.BusySlot> busySlots() {
        return repository.findBusySlotsFrom(LocalDateTime.now(zone).toLocalDate());
    }

    @Transactional(readOnly = true)
    public List<Booking> all() {
        return repository.findAllByOrderByDateAscTimeAsc();
    }

    public Booking create(NewBookingRequest request) {
        if (!LocalDateTime.of(request.date(), request.time()).isAfter(LocalDateTime.now(zone))) {
            throw new IllegalArgumentException("Não é possível agendar no passado.");
        }
        Booking booking = new Booking(request.date(), request.time(), request.name().trim(),
                request.email().trim(), request.topic() == null ? "" : request.topic().trim());
        Booking saved;
        try {
            // saveAndFlush força o INSERT agora, para a restrição UNIQUE do banco acusar reserva dupla aqui.
            saved = repository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            throw new SlotTakenException();
        }
        // Já gravado: o aviso roda em segundo plano e uma falha no e-mail não desfaz o agendamento.
        notifier.bookingCreated(saved);
        return saved;
    }

    /** Marca a reunião como realizada. */
    @Transactional
    public Booking complete(UUID id) {
        Booking booking = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Agendamento não encontrado."));
        booking.complete();
        return booking;
    }

    /** Monta a resposta com início/fim no fuso da agenda e o status calculado agora. */
    public BookingResponse toResponse(Booking b) {
        Instant start = LocalDateTime.of(b.getDate(), b.getTime()).atZone(zone).toInstant();
        Instant end = start.plus(meetingLength);
        Instant now = Instant.now();
        Status status = b.getCompletedAt() != null || !now.isBefore(end) ? Status.DONE
                : !now.isBefore(start) ? Status.IN_PROGRESS
                : Status.OPEN;
        return new BookingResponse(b.getId(), b.getDate(), b.getTime(), b.getName(), b.getEmail(),
                b.getTopic(), b.getCreatedAt(), start, end, b.getCompletedAt(), status);
    }

    @Transactional
    public void cancel(UUID id) {
        if (!repository.existsById(id)) {
            throw new NoSuchElementException("Agendamento não encontrado.");
        }
        repository.deleteById(id);
    }
}
