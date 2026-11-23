package com.catedra.turnos.repository;

import com.catedra.turnos.domain.AppointmentHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppointmentHoldRepository extends JpaRepository<AppointmentHold, String> {

    Optional<AppointmentHold> findByHoldIdAndUserId(String holdId, Long userId);
}
