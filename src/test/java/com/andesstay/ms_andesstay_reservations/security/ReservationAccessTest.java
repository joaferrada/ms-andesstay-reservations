package com.andesstay.ms_andesstay_reservations.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationAccessTest {

    private final JwtAuthConverter converter = new JwtAuthConverter();

    @Test
    void conversorMapeaRolesYUsaPreferredUsername() {
        Authentication auth = autenticar("ana@andesstay.cl", List.of("Cliente_Dominio"));

        assertThat(auth.getName()).isEqualTo("ana@andesstay.cl");
        assertThat(auth.getAuthorities()).extracting("authority").contains("ROLE_Cliente_Dominio");
    }

    @Test
    void tokenV1SinPreferredUsernameUsaUpnYNoSub() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
                .subject("AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy782bbtaQ")
                .claim("upn", "ana@andesstay.cl")
                .claim("unique_name", "otro@andesstay.cl")
                .claim("preferred_username", " ")
                .build();

        assertThat(converter.convert(jwt).getName()).isEqualTo("ana@andesstay.cl");
    }

    @Test
    void sinUpnUsaUniqueNameYLuegoEmail() {
        Jwt uniqueName = Jwt.withTokenValue("t").header("alg", "none").subject("s")
                .claim("unique_name", "ana@andesstay.cl").claim("email", "otro@x.cl").build();
        Jwt email = Jwt.withTokenValue("t").header("alg", "none").subject("s").claim("email", "ana@x.cl").build();

        assertThat(converter.convert(uniqueName).getName()).isEqualTo("ana@andesstay.cl");
        assertThat(converter.convert(email).getName()).isEqualTo("ana@x.cl");
    }

    @Test
    void sinPreferredUsernameUsaSub() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("sub-123").build();

        assertThat(converter.convert(jwt).getName()).isEqualTo("sub-123");
    }

    @Test
    void clienteFiltraPorSuUsername() {
        assertThat(ReservationAccess.filtroCliente(autenticar("ana@andesstay.cl", List.of("Cliente_Dominio"))))
                .isEqualTo("ana@andesstay.cl");
    }

    @Test
    void usuarioSinRolesFiltraPorSuUsername() {
        assertThat(ReservationAccess.filtroCliente(autenticar("ana@andesstay.cl", List.of())))
                .isEqualTo("ana@andesstay.cl");
    }

    @Test
    void adminOperadorYAuditorVenTodo() {
        assertThat(ReservationAccess.filtroCliente(autenticar("admin@andesstay.cl", List.of("Admin")))).isNull();
        assertThat(ReservationAccess.filtroCliente(autenticar("op@andesstay.cl", List.of("Operador_Dominio")))).isNull();
        assertThat(ReservationAccess.filtroCliente(autenticar("audit@andesstay.cl", List.of("Auditor")))).isNull();
    }

    private Authentication autenticar(String username, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("sub-" + username)
                .claim("preferred_username", username)
                .claim("roles", roles)
                .build();
        return converter.convert(jwt);
    }
}
