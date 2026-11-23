package com.agendaonline.backend.booking;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Formatos JSON trocados com o front. Datas como "YYYY-MM-DD" e horários como "HH:mm". */
public final class BookingDtos {

    private BookingDtos() {
    }

    /** Pedido de agendamento enviado pelo visitante. */
    public record NewBookingRequest(
            @NotNull LocalDate date,
            @NotNull @JsonFormat(pattern = "HH:mm") LocalTime time,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 500) String topic) {
    }

    /**
     * Situação da reunião.
     * OPEN = ainda não começou; IN_PROGRESS = no horário agora;
     * DONE = marcada como realizada ou já passou do horário de término.
     */
    public enum Status { OPEN, IN_PROGRESS, DONE }

    /**
     * Agendamento completo (com dados pessoais).
     * startsAt/endsAt são instantes absolutos (UTC), para o front recalcular o status com o relógio.
     */
    public record BookingResponse(
            UUID id,
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            String name,
            String email,
            String topic,
            Instant createdAt,
            Instant startsAt,
            Instant endsAt,
            Instant completedAt,
            Status status) {
    }

    /** Horário ocupado, sem dados pessoais (visão pública). */
    public record BusySlot(LocalDate date, @JsonFormat(pattern = "HH:mm") LocalTime time) {
    }
}
