package com.agendaonline.backend.booking;

import com.agendaonline.backend.booking.BookingDtos.BookingResponse;
import com.agendaonline.backend.booking.BookingDtos.BusySlot;
import com.agendaonline.backend.booking.BookingDtos.NewBookingRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rotas públicas, usadas pelo visitante para ver horários e marcar uma reunião. */
@RestController
@RequestMapping("/api")
public class VisitorBookingController {

    private final BookingService service;

    public VisitorBookingController(BookingService service) {
        this.service = service;
    }

    /** Horários ocupados a partir de hoje, sem nome ou e-mail de ninguém. */
    @GetMapping("/slots/busy")
    public List<BusySlot> busySlots() {
        return service.busySlots();
    }

    /** Cria um agendamento. Responde 409 se o horário já foi reservado. */
    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody NewBookingRequest request) {
        return service.toResponse(service.create(request));
    }
}
