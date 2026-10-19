package com.andesstay.ms_andesstay_reservations.controller;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.security.HuespedResolver;
import com.andesstay.ms_andesstay_reservations.security.JwtAuthConverter;
import com.andesstay.ms_andesstay_reservations.security.SecurityConfig;
import com.andesstay.ms_andesstay_reservations.service.DatosHuesped;
import com.andesstay.ms_andesstay_reservations.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autorizacion por rol de cada endpoint (misma matriz que el BFF).
 */
@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, JwtAuthConverter.class, HuespedResolver.class})
class ReservationControllerSecurityTest {

    private static final String BODY =
            "{\"alojamientoNombre\":\"Cabana Lago\",\"huespedNombre\":\"Luis Soto\",\"huespedEmail\":\"luis@correo.cl\"}";
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
        when(reservationService.crear(any(), any(), any())).thenReturn(reserva);
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

    @Test
    void clienteCreaConLosDatosDelTokenAunqueElBodyTraigaOtros() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(usuario("Cliente_Dominio")))
                .andExpect(status().isOk());

        ArgumentCaptor<DatosHuesped> huesped = ArgumentCaptor.forClass(DatosHuesped.class);
        verify(reservationService).crear(any(), huesped.capture(), eq("ana@andesstay.cl"));
        assertThat(huesped.getValue()).isEqualTo(new DatosHuesped("Ana Rojas", "ana@andesstay.cl"));
    }

    @Test
    void adminSinEmailDelHuespedRecibe400ConMensajeClaro() throws Exception {
        mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alojamientoNombre\":\"Cabana Lago\",\"huespedNombre\":\"Luis Soto\"}")
                        .with(usuario("Admin")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Datos del huesped invalidos: huespedEmail: es obligatorio"));
        verify(reservationService, never()).crear(any(), any(), any());
    }

    private void assertStatus(MockHttpServletRequestBuilder request, String rol, int esperado) throws Exception {
        mockMvc.perform(request.with(usuario(rol))).andExpect(status().is(esperado));
    }

    /** Principal = ana@andesstay.cl (como el upn de Entra), claim name = "Ana Rojas". */
    private static RequestPostProcessor usuario(String rol) {
        return jwt().jwt(j -> j.subject("ana@andesstay.cl").claim("name", "Ana Rojas"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }
}
