package com.andesstay.ms_andesstay_reservations.controller;

import com.andesstay.ms_andesstay_reservations.dto.StatusUpdateRequest;
import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import com.andesstay.ms_andesstay_reservations.security.ReservationAccess;
import com.andesstay.ms_andesstay_reservations.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    // 1. READ (Admin/Operador/Auditor: todas; Cliente: solo las suyas. Opcional ?status=CONFIRMADA)
    @GetMapping
    @PreAuthorize("hasAnyRole('Admin','Operador_Dominio','Cliente_Dominio','Auditor')")
    public List<ReservationEntity> listarReservas(@RequestParam(required = false) ReservationStatus status,
                                                  Authentication authentication) {
        return reservationService.listar(status, ReservationAccess.filtroCliente(authentication));
    }

    // 2. READ (Buscar por ID; 404 si es de otro cliente)
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin','Operador_Dominio','Cliente_Dominio','Auditor')")
    public ResponseEntity<ReservationEntity> obtenerReservaPorId(@PathVariable Long id, Authentication authentication) {
        return reservationService.buscarPorId(id, ReservationAccess.filtroCliente(authentication))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 3. CREATE (Crear nueva reserva, siempre parte en CREADA)
    @PostMapping
    @PreAuthorize("hasAnyRole('Admin','Operador_Dominio','Cliente_Dominio')")
    public ReservationEntity crearReserva(@RequestBody ReservationEntity reserva, Authentication authentication) {
        return reservationService.crear(reserva, authentication.getName());
    }

    // 4. UPDATE (Actualizar reserva existente; el Cliente solo las suyas)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin','Operador_Dominio','Cliente_Dominio')")
    public ResponseEntity<ReservationEntity> actualizarReserva(@PathVariable Long id, @RequestBody ReservationEntity reservaDetalles, Authentication authentication) {
        return reservationService.actualizar(id, reservaDetalles, ReservationAccess.filtroCliente(authentication))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 5. DELETE (Eliminar reserva; solo Admin)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Void> eliminarReserva(@PathVariable Long id) {
        return reservationService.eliminar(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // 6. CAMBIO DE ESTADO (EP3). 404 si no existe, 409 si la transicion no esta permitida
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('Admin','Operador_Dominio')")
    public ReservationEntity cambiarEstado(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return reservationService.cambiarEstado(id, request.status());
    }
}
