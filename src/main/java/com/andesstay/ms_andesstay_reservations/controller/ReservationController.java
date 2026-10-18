package com.andesstay.ms_andesstay_reservations.controller;

import com.andesstay.ms_andesstay_reservations.dto.StatusUpdateRequest;
import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
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

    // 1. READ (Listar todas, opcionalmente filtradas por ?status=CONFIRMADA)
    @GetMapping
    public List<ReservationEntity> listarReservas(@RequestParam(required = false) ReservationStatus status,
                                                  Authentication authentication) {
        return reservationService.listar(status);
    }

    // 2. READ (Buscar por ID)
    @GetMapping("/{id}")
    public ResponseEntity<ReservationEntity> obtenerReservaPorId(@PathVariable Long id) {
        return reservationService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 3. CREATE (Crear nueva reserva, siempre parte en CREADA)
    @PostMapping
    @PreAuthorize("hasRole('Cliente_Dominio') or hasRole('Admin')")
    public ReservationEntity crearReserva(@RequestBody ReservationEntity reserva, Authentication authentication) {
        return reservationService.crear(reserva, authentication.getName());
    }

    // 4. UPDATE (Actualizar reserva existente)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Cliente_Dominio') or hasRole('Admin')")
    public ResponseEntity<ReservationEntity> actualizarReserva(@PathVariable Long id, @RequestBody ReservationEntity reservaDetalles, Authentication authentication) {
        return reservationService.actualizar(id, reservaDetalles)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 5. DELETE (Eliminar reserva)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin') or hasRole('Operador_Dominio')")
    public ResponseEntity<Void> eliminarReserva(@PathVariable Long id) {
        return reservationService.eliminar(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    // 6. CAMBIO DE ESTADO (EP3). 404 si no existe, 409 si la transicion no esta permitida
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('Admin') or hasRole('Operador_Dominio')")
    public ReservationEntity cambiarEstado(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return reservationService.cambiarEstado(id, request.status());
    }
}
