package com.andesstay.ms_andesstay_reservations.client;

import com.andesstay.ms_andesstay_reservations.entity.ReservationEntity;
import com.andesstay.ms_andesstay_reservations.entity.ReservationStatus;
import com.andesstay.ms_andesstay_reservations.exception.CatalogConflictException;
import com.andesstay.ms_andesstay_reservations.exception.CatalogUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Clientes REST de catalog y audit contra un servidor simulado (sin llamadas reales).
 */
class RestClientsTest {

    private MockRestServiceServer server;
    private RestCatalogClient catalog;
    private RestAuditClient audit;

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("token-operador").header("alg", "none").subject("op@andesstay.cl").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        catalog = new RestCatalogClient(RestClientConfig.conJwt(builder.clone(), "http://catalog:8085"));
        audit = new RestAuditClient(RestClientConfig.conJwt(builder.clone(), "http://audit:8086"));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reservarCupoPropagaElJwt() {
        server.expect(requestTo("http://catalog:8085/api/catalog/units/10/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-operador"))
                .andRespond(withSuccess("{\"id\":10,\"cuposDisponibles\":2}", MediaType.APPLICATION_JSON));

        catalog.reservarCupo(10L);

        server.verify();
    }

    @Test
    void catalogSinCuposSeTraduceA409ConSuMensaje() {
        server.expect(requestTo("http://catalog:8085/api/catalog/units/10/reserve"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"message\":\"La unidad 10 (Cabana Lago) no tiene cupos disponibles (0 de 3)\"}"));

        assertThatThrownBy(() -> catalog.reservarCupo(10L))
                .isInstanceOf(CatalogConflictException.class)
                .hasMessage("No se puede confirmar la reserva: La unidad 10 (Cabana Lago) no tiene cupos disponibles (0 de 3)");
    }

    @Test
    void unidadInexistenteTambienEs409() {
        server.expect(requestTo("http://catalog:8085/api/catalog/units/99/reserve"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> catalog.reservarCupo(99L))
                .isInstanceOf(CatalogConflictException.class)
                .hasMessageContaining("no existe en el catalogo");
    }

    @Test
    void catalogCaidoAlReservarEs503() {
        server.expect(requestTo("http://catalog:8085/api/catalog/units/10/reserve"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> catalog.reservarCupo(10L))
                .isInstanceOf(CatalogUnavailableException.class)
                .hasMessageContaining("ms-andesstay-catalog no responde");
    }

    @Test
    void catalogCaidoAlLiberarNoRompeNada() {
        server.expect(requestTo("http://catalog:8085/api/catalog/units/10/release"))
                .andRespond(withServerError());

        assertThatCode(() -> catalog.liberarCupo(10L)).doesNotThrowAnyException();
    }

    @Test
    void registraCambioDeEstadoEnAuditConElActor() {
        server.expect(requestTo("http://audit:8086/api/audit/events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-operador"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tipo").value("CAMBIO_ESTADO"))
                .andExpect(jsonPath("$.reservationId").value(1))
                .andExpect(jsonPath("$.actor").value("op@andesstay.cl"))
                .andExpect(jsonPath("$.estadoAnterior").value("CREADA"))
                .andExpect(jsonPath("$.estadoNuevo").value("CONFIRMADA"))
                .andExpect(jsonPath("$.occurredAt").exists())
                .andRespond(withStatus(HttpStatus.CREATED));

        audit.registrarCambioEstado(reserva(ReservationStatus.CONFIRMADA), ReservationStatus.CREADA, "op@andesstay.cl");

        server.verify();
    }

    @Test
    void auditCaidoNoRompeNada() {
        server.expect(requestTo("http://audit:8086/api/audit/events"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatCode(() -> audit.registrarCreacion(reserva(ReservationStatus.CREADA), "op@andesstay.cl"))
                .doesNotThrowAnyException();
    }

    @Test
    void auditConErrorNoRompeNada() {
        server.expect(requestTo("http://audit:8086/api/audit/events"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatCode(() -> audit.registrarEliminacion(reserva(ReservationStatus.CANCELADA), "op@andesstay.cl"))
                .doesNotThrowAnyException();
    }

    private static ReservationEntity reserva(ReservationStatus estado) {
        ReservationEntity r = new ReservationEntity();
        r.setId(1L);
        r.setAlojamientoNombre("Cabana Lago");
        r.setHuespedEmail("ana@andesstay.cl");
        r.setEstado(estado);
        return r;
    }
}
