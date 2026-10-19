package com.andesstay.ms_andesstay_reservations.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * URLs de los microservicios que llama reservations ({@code andesstay.services.*} en application.yaml).
 */
@Validated
@ConfigurationProperties(prefix = "andesstay.services")
public record ServicesProperties(
        @Valid @NotNull Service catalog,
        @Valid @NotNull Service audit,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {

    public record Service(@NotBlank String url) {
    }
}
