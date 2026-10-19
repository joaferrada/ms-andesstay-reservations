package com.andesstay.ms_andesstay_reservations.service;

/**
 * Huesped de una reserva, ya resuelto (desde el token o desde el body) y validado.
 */
public record DatosHuesped(String nombre, String email) {
}
