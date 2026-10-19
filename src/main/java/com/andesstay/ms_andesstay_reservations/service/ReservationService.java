package com.andesstay.ms_andesstay_reservations.service;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import com.andesstay.ms_andesstay_reservations.exception.InvalidStatusTransitionException;
import com.andesstay.ms_andesstay_reservations.exception.ReservationNotFoundException;
import com.andesstay.ms_andesstay_reservations.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Logica de negocio de reservas. No conoce RabbitMQ: publica comandos a traves de
 * {@link ReservationNotificationPublisher}.
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationNotificationPublisher notificationPublisher;

    public ReservationService(ReservationRepository reservationRepository,
                              ReservationNotificationPublisher notificationPublisher) {
        this.reservationRepository = reservationRepository;
        this.notificationPublisher = notificationPublisher;
    }

    /**
     * @param clienteUsername {@code null} = todas (Admin/Operador); si no, solo las de ese cliente
     */
    public List<ReservationEntity> listar(ReservationStatus estado, String clienteUsername) {
        if (clienteUsername == null) {
            return estado == null
                    ? reservationRepository.findAll()
                    : reservationRepository.findByEstado(estado);
        }
        return estado == null
                ? reservationRepository.findByClienteUsername(clienteUsername)
                : reservationRepository.findByClienteUsernameAndEstado(clienteUsername, estado);
    }

    /**
     * Una reserva ajena se trata como inexistente (404) para no revelar que existe.
     */
    public Optional<ReservationEntity> buscarPorId(Long id, String clienteUsername) {
        return reservationRepository.findById(id)
                .filter(r -> clienteUsername == null || clienteUsername.equals(r.getClienteUsername()));
    }

    /**
     * Una reserva nueva siempre parte en CREADA, sin importar lo que venga en el body.
     */
    public ReservationEntity crear(ReservationEntity reserva, String clienteUsername) {
        reserva.setId(null);
        reserva.setClienteUsername(clienteUsername);
        reserva.setEstado(ReservationStatus.CREADA);
        ReservationEntity guardada = reservationRepository.save(reserva);
        notificationPublisher.publishStatusChanged(guardada, null);
        return guardada;
    }

    /**
     * Actualiza datos de la reserva (no el estado; para eso existe {@link #cambiarEstado}).
     */
    public Optional<ReservationEntity> actualizar(Long id, ReservationEntity detalles, String clienteUsername) {
        return buscarPorId(id, clienteUsername)
                .map(existente -> {
                    existente.setAlojamientoNombre(detalles.getAlojamientoNombre());
                    existente.setFechaInicio(detalles.getFechaInicio());
                    existente.setFechaFin(detalles.getFechaFin());
                    existente.setPrecioTotal(detalles.getPrecioTotal());
                    return reservationRepository.save(existente);
                });
    }

    public boolean eliminar(Long id) {
        if (!reservationRepository.existsById(id)) {
            return false;
        }
        reservationRepository.deleteById(id);
        return true;
    }

    /**
     * Aplica la maquina de estados de {@link ReservationStatus}.
     *
     * @throws ReservationNotFoundException      si la reserva no existe (404)
     * @throws InvalidStatusTransitionException si la transicion no esta permitida (409)
     */
    public ReservationEntity cambiarEstado(Long id, ReservationStatus nuevoEstado) {
        ReservationEntity reserva = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        ReservationStatus anterior = reserva.getEstado();
        if (!anterior.puedeTransicionarA(nuevoEstado)) {
            throw new InvalidStatusTransitionException(anterior, nuevoEstado);
        }

        reserva.setEstado(nuevoEstado);
        ReservationEntity guardada = reservationRepository.save(reserva);
        notificationPublisher.publishStatusChanged(guardada, anterior);
        return guardada;
    }
}
