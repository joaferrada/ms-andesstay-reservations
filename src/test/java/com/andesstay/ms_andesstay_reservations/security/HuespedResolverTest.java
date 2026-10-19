package com.andesstay.ms_andesstay_reservations.security;

import com.andesstay.ms_andesstay_reservations.dto.ReservationCreateRequest;
import com.andesstay.ms_andesstay_reservations.exception.InvalidGuestDataException;
import com.andesstay.ms_andesstay_reservations.service.DatosHuesped;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HuespedResolverTest {

    private final JwtAuthConverter converter = new JwtAuthConverter();
    private final HuespedResolver resolver =
            new HuespedResolver(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void clienteTomaNombreYEmailDelTokenEIgnoraElBody() {
        Authentication cliente = autenticar("Cliente_Dominio", "ana@andesstay.cl", "Ana Rojas");

        DatosHuesped huesped = resolver.resolver(body("Otro Nombre", "otro@correo.cl"), cliente);

        assertThat(huesped).isEqualTo(new DatosHuesped("Ana Rojas", "ana@andesstay.cl"));
    }

    @Test
    void clienteSinClaimNameRecibe400() {
        Authentication cliente = autenticar("Cliente_Dominio", "ana@andesstay.cl", null);

        assertThatThrownBy(() -> resolver.resolver(body("Ana", "ana@andesstay.cl"), cliente))
                .isInstanceOf(InvalidGuestDataException.class)
                .hasMessageContaining("tomados del token")
                .hasMessageContaining("huespedNombre: es obligatorio");
    }

    @Test
    void adminTomaLosDatosDelBody() {
        Authentication admin = autenticar("Admin", "admin@andesstay.cl", "Admin AndesStay");

        DatosHuesped huesped = resolver.resolver(body(" Luis Soto ", "luis@correo.cl"), admin);

        assertThat(huesped).isEqualTo(new DatosHuesped("Luis Soto", "luis@correo.cl"));
    }

    @Test
    void adminSinEmailRecibe400() {
        Authentication admin = autenticar("Admin", "admin@andesstay.cl", "Admin AndesStay");

        assertThatThrownBy(() -> resolver.resolver(body("Luis Soto", null), admin))
                .isInstanceOf(InvalidGuestDataException.class)
                .hasMessage("Datos del huesped invalidos: huespedEmail: es obligatorio");
    }

    @Test
    void adminConEmailInvalidoRecibe400() {
        Authentication admin = autenticar("Admin", "admin@andesstay.cl", "Admin AndesStay");

        assertThatThrownBy(() -> resolver.resolver(body("Luis Soto", "no-es-email"), admin))
                .isInstanceOf(InvalidGuestDataException.class)
                .hasMessageContaining("huespedEmail: debe ser un email valido");
    }

    @Test
    void operadorTambienIngresaElHuesped() {
        Authentication operador = autenticar("Operador_Dominio", "op@andesstay.cl", "Operador");

        assertThat(resolver.resolver(body("Luis Soto", "luis@correo.cl"), operador).email())
                .isEqualTo("luis@correo.cl");
    }

    private static ReservationCreateRequest body(String nombre, String email) {
        return new ReservationCreateRequest("Cabana Lago", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5),
                250000.0, nombre, email);
    }

    private Authentication autenticar(String rol, String upn, String name) {
        Jwt.Builder jwt = Jwt.withTokenValue("t").header("alg", "none")
                .subject("sub-opaco")
                .claim("upn", upn)
                .claim("roles", List.of(rol));
        if (name != null) {
            jwt.claim("name", name);
        }
        return converter.convert(jwt.build());
    }
}
