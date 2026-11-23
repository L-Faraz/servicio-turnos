package com.catedra.turnos.repository;

import com.catedra.turnos.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLogin(String login);

    boolean existsByLogin(String login);

    boolean existsByEmail(String email);
}
