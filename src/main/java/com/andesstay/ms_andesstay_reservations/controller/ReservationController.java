package com.andesstay.ms_andesstay_reservations.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    @GetMapping
    public Map<String, Object> listarReservas(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("origen", "ms-andesstay-reservations");
        response.put("mensaje", "Listado de reservas obtenido correctamente desde el microservicio");
        response.put("procesadoPara", authentication.getName());
        return response;
    }

    @PostMapping
    @PreAuthorize("hasRole('Cliente_Dominio') or hasRole('Admin')")
    public Map<String, Object> crearReserva(@RequestBody Map<String, Object> reservaDto, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("origen", "ms-andesstay-reservations");
        response.put("mensaje", "Reserva creada exitosamente en la base de datos de dominio");
        response.put("creadoPor", authentication.getName());
        response.put("datos", reservaDto);
        return response;
    }
}