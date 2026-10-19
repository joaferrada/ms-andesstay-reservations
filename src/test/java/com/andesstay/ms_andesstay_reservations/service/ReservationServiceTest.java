package com.andesstay.ms_andesstay_reservations.service;

import com.andesstay.ms_andesstay_reservations.dto.ReservationCreateRequest;
import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import com.andesstay.ms_andesstay_reservations.exception.InvalidStatusTransitionException;
import com.andesstay.ms_andesstay_reservations.exception.ReservationNotFoundException;
import com.andesstay.ms_andesstay_reservations.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationServiceTest {

    private ReservationRepository repository;
    private ReservationNotificationPublisher publisher;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReservationRepository.class);
        publisher = mock(ReservationNotificationPublisher.class);
        service = new ReservationService(repository, publisher);
        when(repository.save(any(ReservationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void crearSiempreParteEnCreadaYPublica() {
        ReservationCreateRequest body = new ReservationCreateRequest("Cabana Lago", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 5), 250000.0, "ignorado", "ignorado@x.cl");

        ReservationEntity creada = service.crear(body, new DatosHuesped("Ana Rojas", "ana@andesstay.cl"),
                "operador@andesstay.cl");

        assertThat(creada.getEstado()).isEqualTo(ReservationStatus.CREADA);
        assertThat(creada.getId()).isNull();
        assertThat(creada.getAlojamientoNombre()).isEqualTo("Cabana Lago");
        assertThat(creada.getHuespedNombre()).isEqualTo("Ana Rojas");
        assertThat(creada.getHuespedEmail()).isEqualTo("ana@andesstay.cl");
        assertThat(creada.getCreadoPor()).isEqualTo("operador@andesstay.cl");
        verify(publisher).publishStatusChanged(creada, null);
    }

    @Test
    void transicionValidaGuardaYPublica() {
        ReservationEntity r = reserva(1L, ReservationStatus.CREADA);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        ReservationEntity resultado = service.cambiarEstado(1L, ReservationStatus.CONFIRMADA);

        assertThat(resultado.getEstado()).isEqualTo(ReservationStatus.CONFIRMADA);
        verify(repository).save(r);
        verify(publisher).publishStatusChanged(r, ReservationStatus.CREADA);
    }

    @Test
    void checkinSinConfirmarLanza409YNoPublica() {
        when(repository.findById(1L)).thenReturn(Optional.of(reserva(1L, ReservationStatus.CREADA)));

        assertThatThrownBy(() -> service.cambiarEstado(1L, ReservationStatus.CHECKIN_PENDIENTE))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("CREADA -> CHECKIN_PENDIENTE");
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void reservaInexistenteLanza404() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(5L, ReservationStatus.CONFIRMADA))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void listarSinFiltroDevuelveTodas() {
        service.listar(null, null);
        service.listar(ReservationStatus.CONFIRMADA, null);

        verify(repository).findAll();
        verify(repository).findByEstado(ReservationStatus.CONFIRMADA);
    }

    @Test
    void listarConClienteSoloDevuelveLasSuyas() {
        service.listar(null, "ana@andesstay.cl");
        service.listar(ReservationStatus.CONFIRMADA, "ana@andesstay.cl");

        verify(repository).findByHuespedEmailIgnoreCase("ana@andesstay.cl");
        verify(repository).findByHuespedEmailIgnoreCaseAndEstado("ana@andesstay.cl", ReservationStatus.CONFIRMADA);
        verify(repository, never()).findAll();
    }

    @Test
    void buscarReservaAjenaComoClienteDevuelveVacio() {
        ReservationEntity r = reserva(1L, ReservationStatus.CREADA);
        r.setHuespedEmail("ana@andesstay.cl");
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        assertThat(service.buscarPorId(1L, "ANA@andesstay.cl")).contains(r);
        assertThat(service.buscarPorId(1L, "luis@andesstay.cl")).isEmpty();
        assertThat(service.buscarPorId(1L, null)).contains(r);
    }

    @Test
    void actualizarReservaAjenaComoClienteNoGuarda() {
        ReservationEntity r = reserva(1L, ReservationStatus.CREADA);
        r.setHuespedEmail("ana@andesstay.cl");
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        assertThat(service.actualizar(1L, new ReservationEntity(), "luis@andesstay.cl")).isEmpty();
        verify(repository, never()).save(any());
    }

    private static ReservationEntity reserva(Long id, ReservationStatus estado) {
        ReservationEntity r = new ReservationEntity();
        r.setId(id);
        r.setEstado(estado);
        return r;
    }
}
