package com.catedra.turnos.domain.enums;

public enum ProcessStatus {
    HELD,
    WAITING_FOR_PHONE,
    PHONE_SUBMITTED,
    CONFIRMED,
    REJECTED,
    EXPIRED,
    INVALID,
    CANCELLED,
    FAILED;

    public boolean isFinal() {
        return this == CONFIRMED || this == EXPIRED || this == INVALID || this == CANCELLED || this == FAILED;
    }
}
