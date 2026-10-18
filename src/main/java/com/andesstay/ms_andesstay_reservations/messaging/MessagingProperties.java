package com.andesstay.ms_andesstay_reservations.messaging;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Nombres de exchanges, colas y routing keys. Se leen de application.yaml
 * ({@code andesstay.messaging.*}); ningun nombre queda escrito en el codigo de negocio.
 */
@Validated
@ConfigurationProperties(prefix = "andesstay.messaging")
public record MessagingProperties(
        @NotBlank String source,
        @NotNull Duration dlqRetention,
        @Valid @NotNull Exchanges exchanges,
        @Valid @NotNull Routes routes,
        @Valid @NotNull Publishing publishing) {

    /** Exchanges durables del caso. */
    public record Exchanges(
            @NotBlank String direct,
            @NotBlank String topic,
            @NotBlank String deadLetter) {
    }

    public record Routes(
            @Valid @NotNull Route email,
            @Valid @NotNull Route housekeeping,
            @Valid @NotNull Route voucher) {
    }

    /**
     * Una ruta = cola principal + su DLQ + bindings.
     *
     * @param directKey    binding de la cola con cmd.direct (match exacto)
     * @param topicPattern binding de la cola con cmd.topic (patron con * o #)
     * @param deadKey      x-dead-letter-routing-key de la cola y binding de la DLQ con cmd.dead.dlx
     */
    public record Route(
            @NotBlank String queue,
            @NotBlank String dlq,
            @NotBlank String directKey,
            @NotBlank String topicPattern,
            @NotBlank String deadKey) {
    }

    /** Routing keys concretas usadas al publicar en cmd.topic. */
    public record Publishing(
            @NotBlank String housekeepingPreparationKey,
            @NotBlank String housekeepingCleaningKey) {
    }
}
