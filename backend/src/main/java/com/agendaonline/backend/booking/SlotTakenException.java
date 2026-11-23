package com.agendaonline.backend.booking;

public class SlotTakenException extends RuntimeException {

    public SlotTakenException() {
        super("Este horário já está reservado.");
    }
}
