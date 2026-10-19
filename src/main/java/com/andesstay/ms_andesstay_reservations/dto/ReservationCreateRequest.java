package com.andesstay.ms_andesstay_reservations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Body de POST /api/reservations. id, estado y creadoPor nunca se toman del body.
 *
 * huespedNombre/huespedEmail solo se usan (y se validan, grupo {@link DatosHuesped}) cuando crea
 * Admin u Operador; para un Cliente_Dominio salen del token y lo que venga aqui se ignora.
 */
public record ReservationCreateRequest(
        String alojamientoNombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Double precioTotal,
        @NotBlank(groups = DatosHuesped.class, message = "es obligatorio") String huespedNombre,
        @NotBlank(groups = DatosHuesped.class, message = "es obligatorio")
        @Email(groups = DatosHuesped.class, message = "debe ser un email valido") String huespedEmail) {

    /** Grupo de validacion de los datos del huesped. */
    public interface DatosHuesped {
    }
}
