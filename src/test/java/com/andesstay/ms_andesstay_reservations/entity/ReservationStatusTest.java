package com.andesstay.ms_andesstay_reservations.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationStatusTest {

    @ParameterizedTest(name = "{0} -> {1} es valida")
    @CsvSource({
            "CREADA, CONFIRMADA",
            "CREADA, CANCELADA",
            "CONFIRMADA, CHECKIN_PENDIENTE",
            "CONFIRMADA, CANCELADA",
            "CHECKIN_PENDIENTE, EN_ESTADIA",
            "CHECKIN_PENDIENTE, CANCELADA",
            "EN_ESTADIA, CHECKOUT"
    })
    void transicionesValidas(ReservationStatus desde, ReservationStatus hacia) {
        assertThat(desde.puedeTransicionarA(hacia)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} es invalida")
    @CsvSource({
            // Regla del caso: no hay check-in sin confirmar
            "CREADA, CHECKIN_PENDIENTE",
            "CREADA, EN_ESTADIA",
            "CREADA, CHECKOUT",
            "CREADA, CREADA",
            "CONFIRMADA, EN_ESTADIA",
            "EN_ESTADIA, CANCELADA",
            "CHECKOUT, CREADA",
            "CANCELADA, CONFIRMADA"
    })
    void transicionesInvalidas(ReservationStatus desde, ReservationStatus hacia) {
        assertThat(desde.puedeTransicionarA(hacia)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"CHECKOUT", "CANCELADA"})
    void estadosTerminalesNoTienenSalida(ReservationStatus estado) {
        assertThat(estado.siguientesPermitidos()).isEmpty();
    }
}
