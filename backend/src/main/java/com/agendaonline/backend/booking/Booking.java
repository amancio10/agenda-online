package com.agendaonline.backend.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Uma reunião marcada por um visitante. */
@Entity
@Table(name = "booking",
        uniqueConstraints = @UniqueConstraint(name = "uq_booking_slot", columnNames = {"booking_date", "booking_time"}))
public class Booking {

    @Id
    private UUID id;

    @Column(name = "booking_date", nullable = false)
    private LocalDate date;

    @Column(name = "booking_time", nullable = false)
    private LocalTime time;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 500)
    private String topic;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Quando o administrador marcou como realizada (null = não marcou). */
    @Column(name = "completed_at")
    private Instant completedAt;

    protected Booking() {
        // exigido pelo JPA
    }

    public Booking(LocalDate date, LocalTime time, String name, String email, String topic) {
        this.id = UUID.randomUUID();
        this.date = date;
        this.time = time;
        this.name = name;
        this.email = email;
        this.topic = topic;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getTime() {
        return time;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getTopic() {
        return topic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    /** Marca como realizada (mantém a primeira data, se já estava marcada). */
    public void complete() {
        if (completedAt == null) {
            completedAt = Instant.now();
        }
    }
}
