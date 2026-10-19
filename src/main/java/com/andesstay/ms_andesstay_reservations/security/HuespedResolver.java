package com.andesstay.ms_andesstay_reservations.security;

import com.andesstay.ms_andesstay_reservations.dto.ReservationCreateRequest;
import com.andesstay.ms_andesstay_reservations.exception.InvalidGuestDataException;
import com.andesstay.ms_andesstay_reservations.service.DatosHuesped;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decide de donde salen los datos del huesped al crear una reserva:
 * <ul>
 *   <li>Admin u Operador_Dominio (crean a nombre de otro): del body, validados.</li>
 *   <li>Cliente_Dominio: del token (nombre = claim "name", email = principal); el body se ignora.</li>
 * </ul>
 */
@Component
public class HuespedResolver {

    private static final Set<String> ROLES_INGRESAN_HUESPED = Set.of("ROLE_Admin", "ROLE_Operador_Dominio");

    private final Validator validator;

    public HuespedResolver(Validator validator) {
        this.validator = validator;
    }

    public DatosHuesped resolver(ReservationCreateRequest request, Authentication authentication) {
        boolean ingresaHuesped = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_INGRESAN_HUESPED::contains);

        ReservationCreateRequest datos = ingresaHuesped ? request : desdeToken(request, authentication);
        validar(datos, ingresaHuesped ? "" : " (tomados del token)");
        return new DatosHuesped(datos.huespedNombre().trim(), datos.huespedEmail().trim());
    }

    private ReservationCreateRequest desdeToken(ReservationCreateRequest request, Authentication authentication) {
        String nombre = authentication instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getClaimAsString("name")
                : null;
        return new ReservationCreateRequest(request.alojamientoNombre(), request.fechaInicio(), request.fechaFin(),
                request.precioTotal(), nombre, authentication.getName());
    }

    private void validar(ReservationCreateRequest datos, String origen) {
        Set<ConstraintViolation<ReservationCreateRequest>> errores =
                validator.validate(datos, ReservationCreateRequest.DatosHuesped.class);
        if (!errores.isEmpty()) {
            String detalle = errores.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.joining("; "));
            throw new InvalidGuestDataException("Datos del huesped invalidos" + origen + ": " + detalle);
        }
    }
}
