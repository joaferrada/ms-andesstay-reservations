package com.andesstay.ms_andesstay_reservations.service;

import com.andesstay.ms_andesstay_reservations.dto.ReservationCreateRequest;
import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import com.andesstay.ms_andesstay_reservations.exception.CatalogConflictException;
import com.andesstay.ms_andesstay_reservations.exception.InvalidStatusTransitionException;
import com.andesstay.ms_andesstay_reservations.exception.ReservationNotFoundException;
import com.andesstay.ms_andesstay_reservations.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ReservationServiceTest {

    private static final String OPERADOR = "op@andesstay.cl";

    private ReservationRepository repository;
    private ReservationNotificationPublisher publisher;
    private CatalogClient catalog;
    private AuditClient audit;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        repository = mock(ReservationRepository.class);
        publisher = mock(ReservationNotificationPublisher.class);
        catalog = mock(CatalogClient.class);
        audit = mock(AuditClient.class);
        service = new ReservationService(repository, publisher, catalog, audit);
        when(repository.save(any(ReservationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void crearSiempreParteEnCreadaPublicaYAudita() {
        ReservationCreateRequest body = new ReservationCreateRequest(10L, "Cabana Lago", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 5), 250000.0, "ignorado", "ignorado@x.cl");

        ReservationEntity creada = service.crear(body, new DatosHuesped("Ana Rojas", "ana@andesstay.cl"),
                "operador@andesstay.cl");

        assertThat(creada.getEstado()).isEqualTo(ReservationStatus.CREADA);
        assertThat(creada.getId()).isNull();
        assertThat(creada.getUnidadId()).isEqualTo(10L);
        assertThat(creada.getAlojamientoNombre()).isEqualTo("Cabana Lago");
        assertThat(creada.getHuespedNombre()).isEqualTo("Ana Rojas");
        assertThat(creada.getHuespedEmail()).isEqualTo("ana@andesstay.cl");
        assertThat(creada.getCreadoPor()).isEqualTo("operador@andesstay.cl");
        verify(publisher).publishStatusChanged(creada, null);
        verify(audit).registrarCreacion(creada, "operador@andesstay.cl");
        verifyNoInteractions(catalog); // crear no descuenta cupo: eso ocurre al confirmar
    }

    @Test
    void transicionValidaGuardaPublicaYAudita() {
        ReservationEntity r = reserva(1L, ReservationStatus.CREADA, null);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        ReservationEntity resultado = service.cambiarEstado(1L, ReservationStatus.CONFIRMADA, OPERADOR);

        assertThat(resultado.getEstado()).isEqualTo(ReservationStatus.CONFIRMADA);
        verify(repository).save(r);
        verify(publisher).publishStatusChanged(r, ReservationStatus.CREADA);
        verify(audit).registrarCambioEstado(r, ReservationStatus.CREADA, OPERADOR);
        verifyNoInteractions(catalog); // sin unidadId no se toca el catalogo
    }

    @Test
    void confirmarConUnidadDescuentaCupoAntesDeGuardar() {
        ReservationEntity r = reserva(1L, ReservationStatus.CREADA, 10L);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        service.cambiarEstado(1L, ReservationStatus.CONFIRMADA, OPERADOR);

        var orden = inOrder(catalog, repository);
        orden.verify(catalog).reservarCupo(10L);
        orden.verify(repository).save(r);
        verify(catalog, never()).liberarCupo(anyLong());
    }

    @Test
    void confirmarSinCuposLanza409YNoGuardaNiPublica() {
        ReservationEntity r = reserva(1L, ReservationStatus.CREADA, 10L);
        when(repository.findById(1L)).thenReturn(Optional.of(r));
        doThrow(new CatalogConflictException("No se puede confirmar la reserva: la unidad 10 no tiene cupos disponibles"))
                .when(catalog).reservarCupo(10L);

        assertThatThrownBy(() -> service.cambiarEstado(1L, ReservationStatus.CONFIRMADA, OPERADOR))
                .isInstanceOf(CatalogConflictException.class)
                .hasMessageContaining("no tiene cupos disponibles");
        assertThat(r.getEstado()).isEqualTo(ReservationStatus.CREADA);
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher, audit);
    }

    @Test
    void siFallaElGuardadoDevuelveElCupoDescontado() {
        ReservationEntity r = reserva(1L, ReservationStatus.CREADA, 10L);
        when(repository.findById(1L)).thenReturn(Optional.of(r));
        when(repository.save(any())).thenThrow(new IllegalStateException("BD caida"));

        assertThatThrownBy(() -> service.cambiarEstado(1L, ReservationStatus.CONFIRMADA, OPERADOR))
                .isInstanceOf(IllegalStateException.class);
        verify(catalog).reservarCupo(10L);
        verify(catalog).liberarCupo(10L);
    }

    @ParameterizedTest(name = "{0} -> {1} libera cupo: {2}")
    @CsvSource({
            "CONFIRMADA,CANCELADA,true",
            "CHECKIN_PENDIENTE,CANCELADA,true",
            "EN_ESTADIA,CHECKOUT,true",
            "CREADA,CANCELADA,false",
            "CONFIRMADA,CHECKIN_PENDIENTE,false"})
    void liberaCupoSoloSiYaLoHabiaDescontado(ReservationStatus desde, ReservationStatus hacia, boolean libera) {
        ReservationEntity r = reserva(1L, desde, 10L);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        service.cambiarEstado(1L, hacia, OPERADOR);

        verify(catalog, times(libera ? 1 : 0)).liberarCupo(10L);
        verify(catalog, never()).reservarCupo(anyLong());
    }

    @Test
    void checkinSinConfirmarLanza409YNoPublica() {
        when(repository.findById(1L)).thenReturn(Optional.of(reserva(1L, ReservationStatus.CREADA, 10L)));

        assertThatThrownBy(() -> service.cambiarEstado(1L, ReservationStatus.CHECKIN_PENDIENTE, OPERADOR))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("CREADA -> CHECKIN_PENDIENTE");
        verify(repository, never()).save(any());
        verifyNoInteractions(publisher, catalog, audit);
    }

    @Test
    void reservaInexistenteLanza404() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado(5L, ReservationStatus.CONFIRMADA, OPERADOR))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void eliminarAuditaYDevuelveElCupoSiLoOcupaba() {
        ReservationEntity r = reserva(1L, ReservationStatus.CONFIRMADA, 10L);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        assertThat(service.eliminar(1L, "admin@andesstay.cl")).isTrue();

        verify(repository).deleteById(1L);
        verify(catalog).liberarCupo(10L);
        verify(audit).registrarEliminacion(r, "admin@andesstay.cl");
    }

    @Test
    void eliminarInexistenteNoAudita() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThat(service.eliminar(9L, "admin@andesstay.cl")).isFalse();
        verifyNoInteractions(audit, catalog);
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
        ReservationEntity r = reserva(1L, ReservationStatus.CREADA, null);
        r.setHuespedEmail("ana@andesstay.cl");
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        assertThat(service.buscarPorId(1L, "ANA@andesstay.cl")).contains(r);
        assertThat(service.buscarPorId(1L, "luis@andesstay.cl")).isEmpty();
        assertThat(service.buscarPorId(1L, null)).contains(r);
    }

    @Test
    void actualizarReservaAjenaComoClienteNoGuarda() {
        ReservationEntity r = reserva(1L, ReservationStatus.CREADA, null);
        r.setHuespedEmail("ana@andesstay.cl");
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        assertThat(service.actualizar(1L, new ReservationEntity(), "luis@andesstay.cl")).isEmpty();
        verify(repository, never()).save(any());
    }

    private static ReservationEntity reserva(Long id, ReservationStatus estado, Long unidadId) {
        ReservationEntity r = new ReservationEntity();
        r.setId(id);
        r.setEstado(estado);
        r.setUnidadId(unidadId);
        return r;
    }
}
