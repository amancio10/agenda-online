package com.agendaonline.backend.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** Só data e horário: é o que o visitante precisa para saber o que está ocupado. */
    @Query("select new com.agendaonline.backend.booking.BookingDtos$BusySlot(b.date, b.time) "
            + "from Booking b where b.date >= :from order by b.date, b.time")
    List<BookingDtos.BusySlot> findBusySlotsFrom(LocalDate from);

    List<Booking> findAllByOrderByDateAscTimeAsc();
}
