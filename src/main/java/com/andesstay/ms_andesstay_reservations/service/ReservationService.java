package com.andesstay.ms_andesstay_reservations.service;

import com.andesstay.ms_andesstay_reservations.dto.ReservationCreateRequest;
import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import com.andesstay.ms_andesstay_reservations.exception.CatalogConflictException;
import com.andesstay.ms_andesstay_reservations.exception.CatalogUnavailableException;
import com.andesstay.ms_andesstay_reservations.exception.InvalidStatusTransitionException;
import com.andesstay.ms_andesstay_reservations.exception.ReservationNotFoundException;
import com.andesstay.ms_andesstay_reservations.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Logica de negocio de reservas. No conoce RabbitMQ ni RestClient: publica comandos a traves de
 * {@link ReservationNotificationPublisher} y llama a catalog/audit a traves de {@link CatalogClient}
 * y {@link AuditClient}.
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationNotificationPublisher notificationPublisher;
    private final CatalogClient catalogClient;
    private final AuditClient auditClient;

    public ReservationService(ReservationRepository reservationRepository,
                              ReservationNotificationPublisher notificationPublisher,
                              CatalogClient catalogClient,
                              AuditClient auditClient) {
        this.reservationRepository = reservationRepository;
        this.notificationPublisher = notificationPublisher;
        this.catalogClient = catalogClient;
        this.auditClient = auditClient;
    }

    /**
     * @param huespedEmail {@code null} = todas (Admin/Operador/Auditor); si no, solo las de ese huesped
     */
    public List<ReservationEntity> listar(ReservationStatus estado, String huespedEmail) {
        if (huespedEmail == null) {
            return estado == null
                    ? reservationRepository.findAll()
                    : reservationRepository.findByEstado(estado);
        }
        return estado == null
                ? reservationRepository.findByHuespedEmailIgnoreCase(huespedEmail)
                : reservationRepository.findByHuespedEmailIgnoreCaseAndEstado(huespedEmail, estado);
    }

    /**
     * Una reserva ajena se trata como inexistente (404) para no revelar que existe.
     */
    public Optional<ReservationEntity> buscarPorId(Long id, String huespedEmail) {
        return reservationRepository.findById(id)
                .filter(r -> huespedEmail == null || huespedEmail.equalsIgnoreCase(r.getHuespedEmail()));
    }

    /**
     * Una reserva nueva siempre parte en CREADA, sin importar lo que venga en el body.
     * Registra RESERVA_CREADA en audit (si audit no responde, la reserva se crea igual).
     *
     * @param huesped   datos del huesped ya resueltos y validados (ver HuespedResolver)
     * @param creadoPor principal del token de quien crea
     */
    public ReservationEntity crear(ReservationCreateRequest request, DatosHuesped huesped, String creadoPor) {
        ReservationEntity reserva = new ReservationEntity();
        reserva.setUnidadId(request.unidadId());
        reserva.setAlojamientoNombre(request.alojamientoNombre());
        reserva.setFechaInicio(request.fechaInicio());
        reserva.setFechaFin(request.fechaFin());
        reserva.setPrecioTotal(request.precioTotal());
        reserva.setHuespedNombre(huesped.nombre());
        reserva.setHuespedEmail(huesped.email());
        reserva.setCreadoPor(creadoPor);
        reserva.setClienteUsername(creadoPor);
        reserva.setEstado(ReservationStatus.CREADA);
        ReservationEntity guardada = reservationRepository.save(reserva);
        notificationPublisher.publishStatusChanged(guardada, null);
        auditClient.registrarCreacion(guardada, creadoPor);
        return guardada;
    }

    /**
     * Actualiza datos de la reserva (no el estado; para eso existe {@link #cambiarEstado}).
     */
    public Optional<ReservationEntity> actualizar(Long id, ReservationEntity detalles, String huespedEmail) {
        return buscarPorId(id, huespedEmail)
                .map(existente -> {
                    existente.setAlojamientoNombre(detalles.getAlojamientoNombre());
                    existente.setFechaInicio(detalles.getFechaInicio());
                    existente.setFechaFin(detalles.getFechaFin());
                    existente.setPrecioTotal(detalles.getPrecioTotal());
                    return reservationRepository.save(existente);
                });
    }

    /**
     * Si la reserva ocupaba un cupo (CONFIRMADA..EN_ESTADIA) lo devuelve al catalogo.
     * Registra RESERVA_ELIMINADA en audit.
     */
    public boolean eliminar(Long id, String actor) {
        Optional<ReservationEntity> encontrada = reservationRepository.findById(id);
        if (encontrada.isEmpty()) {
            return false;
        }
        ReservationEntity reserva = encontrada.get();
        reservationRepository.deleteById(id);
        if (reserva.getUnidadId() != null && reserva.getEstado() != null && reserva.getEstado().ocupaCupo()) {
            catalogClient.liberarCupo(reserva.getUnidadId());
        }
        auditClient.registrarEliminacion(reserva, actor);
        return true;
    }

    /**
     * Aplica la maquina de estados de {@link ReservationStatus} y sincroniza los cupos del catalogo:
     * <ul>
     *   <li>a CONFIRMADA con unidadId: descuenta un cupo antes de guardar (si catalog lo rechaza, no se confirma)</li>
     *   <li>a CANCELADA o CHECKOUT desde un estado que ocupaba cupo: lo devuelve</li>
     * </ul>
     * Registra CAMBIO_ESTADO en audit.
     *
     * @param actor principal del token de quien cambia el estado
     * @throws ReservationNotFoundException      si la reserva no existe (404)
     * @throws InvalidStatusTransitionException si la transicion no esta permitida (409)
     * @throws CatalogConflictException         si la unidad no tiene cupos (409)
     * @throws CatalogUnavailableException      si catalog no responde al confirmar (503)
     */
    public ReservationEntity cambiarEstado(Long id, ReservationStatus nuevoEstado, String actor) {
        ReservationEntity reserva = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        ReservationStatus anterior = reserva.getEstado();
        if (!anterior.puedeTransicionarA(nuevoEstado)) {
            throw new InvalidStatusTransitionException(anterior, nuevoEstado);
        }

        Long unidadId = reserva.getUnidadId();
        boolean descuentaCupo = unidadId != null && nuevoEstado == ReservationStatus.CONFIRMADA;
        boolean liberaCupo = unidadId != null && anterior.ocupaCupo()
                && (nuevoEstado == ReservationStatus.CANCELADA || nuevoEstado == ReservationStatus.CHECKOUT);

        if (descuentaCupo) {
            catalogClient.reservarCupo(unidadId);
        }

        reserva.setEstado(nuevoEstado);
        ReservationEntity guardada;
        try {
            guardada = reservationRepository.save(reserva);
        } catch (RuntimeException ex) {
            if (descuentaCupo) {
                catalogClient.liberarCupo(unidadId); // compensacion: la confirmacion no quedo guardada
            }
            throw ex;
        }

        if (liberaCupo) {
            catalogClient.liberarCupo(unidadId);
        }
        notificationPublisher.publishStatusChanged(guardada, anterior);
        auditClient.registrarCambioEstado(guardada, anterior, actor);
        return guardada;
    }
}
