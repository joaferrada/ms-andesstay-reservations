package com.andesstay.ms_andesstay_reservations.dto;

import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Body de PUT /api/reservations/{id}/status. Ejemplo: {"status": "CONFIRMADA"}
 */
public record StatusUpdateRequest(
        @NotNull(message = "status es obligatorio") ReservationStatus status) {
}
