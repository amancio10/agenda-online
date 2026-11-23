package com.agendaonline.backend.booking;

import com.agendaonline.backend.booking.BookingDtos.BookingResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rotas do administrador (exigem token de login — veja SecurityConfig). */
@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final BookingService service;

    public AdminBookingController(BookingService service) {
        this.service = service;
    }

    /** Todos os agendamentos, com dados de contato. */
    @GetMapping
    public List<BookingResponse> all() {
        return service.all().stream().map(service::toResponse).toList();
    }

    /** Marca como realizada. */
    @PostMapping("/{id}/complete")
    public BookingResponse complete(@PathVariable UUID id) {
        return service.toResponse(service.complete(id));
    }

    /** Cancela um agendamento. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        service.cancel(id);
    }
}
