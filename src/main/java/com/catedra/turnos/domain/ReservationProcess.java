package com.catedra.turnos.domain;

import com.catedra.turnos.domain.enums.ProcessStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reservation_process")
@Getter
@Setter
@NoArgsConstructor
public class ReservationProcess {

    @Id
    @Column(name = "process_id", length = 64)
    private String processId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "hold_id", length = 64)
    private String holdId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "professional_id", nullable = false)
    private Long professionalId;

    @Column(name = "professional_first_name", length = 50)
    private String professionalFirstName;

    @Column(name = "professional_last_name", length = 50)
    private String professionalLastName;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "external_patient_id", nullable = false, length = 100)
    private String externalPatientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProcessStatus status;

    @Column(name = "request_event_id", length = 100)
    private String requestEventId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
