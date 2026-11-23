package com.andesstay.ms_andesstay_reservations.events;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración de los tópicos Kafka que declara reservations (andesstay.kafka.topics en application.yaml).
 * Si falta un valor o es inválido, la aplicación no arranca (fail fast).
 */
@Validated
@ConfigurationProperties(prefix = "andesstay.kafka.topics")
public record KafkaTopicsProperties(@Valid @NotNull TopicSpec reservationEvents) {

    /**
     * @param name              nombre del tópico
     * @param partitions        número de particiones (paralelismo máximo de un consumer group)
     * @param replicas          factor de replicación (copias en brokers distintos)
     * @param retention         cuánto tiempo se guardan los eventos (ej. 7d)
     * @param minInsyncReplicas réplicas que deben confirmar una escritura con acks=all
     */
    public record TopicSpec(
            @NotBlank String name,
            @Positive int partitions,
            @Positive int replicas,
            @NotNull Duration retention,
            @Positive int minInsyncReplicas) {
    }
}
