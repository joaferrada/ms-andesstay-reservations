package com.andesstay.ms_andesstay_reservations.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

/**
 * Regla de visibilidad: Admin y Operador_Dominio ven todas las reservas;
 * cualquier otro usuario (Cliente_Dominio) solo las suyas.
 */
public final class ReservationAccess {

    private static final Set<String> ROLES_VEN_TODO = Set.of("ROLE_Admin", "ROLE_Operador_Dominio");

    private ReservationAccess() {}

    /**
     * @return el username por el que hay que filtrar, o {@code null} si el usuario puede ver todo
     */
    public static String filtroCliente(Authentication authentication) {
        boolean veTodo = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLES_VEN_TODO::contains);
        return veTodo ? null : authentication.getName();
    }
}
