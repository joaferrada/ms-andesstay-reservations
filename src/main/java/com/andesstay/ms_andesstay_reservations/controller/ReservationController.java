package com.andesstay.ms_andesstay_reservations.controller;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.repository.ReservationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationRepository reservationRepository;

    public ReservationController(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    // 1. READ (Listar todas)
    @GetMapping
    public List<ReservationEntity> listarReservas(Authentication authentication) {
        return reservationRepository.findAll();
    }

    // 2. READ (Buscar por ID)
    @GetMapping("/{id}")
    public ResponseEntity<ReservationEntity> obtenerReservaPorId(@PathVariable Long id) {
        return reservationRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 3. CREATE (Crear nueva reserva)
    @PostMapping
    @PreAuthorize("hasRole('Cliente_Dominio') or hasRole('Admin')")
    public ReservationEntity crearReserva(@RequestBody ReservationEntity reserva, Authentication authentication) {
        reserva.setClienteUsername(authentication.getName());
        return reservationRepository.save(reserva);
    }

    // 4. UPDATE (Actualizar reserva existente)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Cliente_Dominio') or hasRole('Admin')")
    public ResponseEntity<ReservationEntity> actualizarReserva(@PathVariable Long id, @RequestBody ReservationEntity reservaDetalles, Authentication authentication) {
        return reservationRepository.findById(id)
                .map(reservaExistente -> {
                    reservaExistente.setAlojamientoNombre(reservaDetalles.getAlojamientoNombre());
                    reservaExistente.setFechaInicio(reservaDetalles.getFechaInicio());
                    reservaExistente.setFechaFin(reservaDetalles.getFechaFin());
                    reservaExistente.setPrecioTotal(reservaDetalles.getPrecioTotal());

                    ReservationEntity actualizada = reservationRepository.save(reservaExistente);
                    return ResponseEntity.ok(actualizada);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 5. DELETE (Eliminar reserva)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin') or hasRole('Operador_Dominio')")
    public ResponseEntity<Void> eliminarReserva(@PathVariable Long id) {
        if (reservationRepository.existsById(id)) {
            reservationRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}