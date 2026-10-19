package com.andesstay.ms_andesstay_reservations.controller;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.security.JwtAuthConverter;
import com.andesstay.ms_andesstay_reservations.security.SecurityConfig;
import com.andesstay.ms_andesstay_reservations.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorizacion por rol de cada endpoint (misma matriz que el BFF).
 */
@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class})
class ReservationControllerSecurityTest {

    private static final String BODY = "{\"alojamientoNombre\":\"Cabana Lago\"}";
    private static final String STATUS_BODY = "{\"status\":\"CONFIRMADA\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        ReservationEntity reserva = new ReservationEntity();
        when(reservationService.buscarPorId(anyLong(), any())).thenReturn(Optional.of(reserva));
        when(reservationService.crear(any(), any())).thenReturn(reserva);
        when(reservationService.actualizar(anyLong(), any(), any())).thenReturn(Optional.of(reserva));
        when(reservationService.cambiarEstado(anyLong(), any())).thenReturn(reserva);
        when(reservationService.eliminar(anyLong())).thenReturn(true);
    }

    @ParameterizedTest(name = "GET /api/reservations como {0} -> {1}")
    @CsvSource({"Admin,200", "Operador_Dominio,200", "Cliente_Dominio,200", "Auditor,200", "Sin_Rol,403"})
    void listar(String rol, int esperado) throws Exception {
        assertStatus(get("/api/reservations"), rol, esperado);
    }

    @ParameterizedTest(name = "GET /api/reservations/1 como {0} -> {1}")
    @CsvSource({"Admin,200", "Operador_Dominio,200", "Cliente_Dominio,200", "Auditor,200", "Sin_Rol,403"})
    void obtenerPorId(String rol, int esperado) throws Exception {
        assertStatus(get("/api/reservations/1"), rol, esperado);
    }

    @ParameterizedTest(name = "POST /api/reservations como {0} -> {1}")
    @CsvSource({"Admin,200", "Operador_Dominio,200", "Cliente_Dominio,200", "Auditor,403", "Sin_Rol,403"})
    void crear(String rol, int esperado) throws Exception {
        assertStatus(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(BODY), rol, esperado);
    }

    @ParameterizedTest(name = "PUT /api/reservations/1 como {0} -> {1}")
    @CsvSource({"Admin,200", "Operador_Dominio,200", "Cliente_Dominio,200", "Auditor,403", "Sin_Rol,403"})
    void actualizar(String rol, int esperado) throws Exception {
        assertStatus(put("/api/reservations/1").contentType(MediaType.APPLICATION_JSON).content(BODY), rol, esperado);
    }

    @ParameterizedTest(name = "PUT /api/reservations/1/status como {0} -> {1}")
    @CsvSource({"Admin,200", "Operador_Dominio,200", "Cliente_Dominio,403", "Auditor,403", "Sin_Rol,403"})
    void cambiarEstado(String rol, int esperado) throws Exception {
        assertStatus(put("/api/reservations/1/status").contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY),
                rol, esperado);
    }

    @ParameterizedTest(name = "DELETE /api/reservations/1 como {0} -> {1}")
    @CsvSource({"Admin,204", "Operador_Dominio,403", "Cliente_Dominio,403", "Auditor,403", "Sin_Rol,403"})
    void eliminar(String rol, int esperado) throws Exception {
        assertStatus(delete("/api/reservations/1"), rol, esperado);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
    }

    private void assertStatus(MockHttpServletRequestBuilder request, String rol, int esperado) throws Exception {
        mockMvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + rol))))
                .andExpect(status().is(esperado));
    }
}
