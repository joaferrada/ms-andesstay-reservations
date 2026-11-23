package com.andesstay.ms_andesstay_reservations.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara los tópicos Kafka que publica reservations.
 *
 * Los brokers tienen KAFKA_AUTO_CREATE_TOPICS_ENABLE=false: ningún tópico se crea solo.
 * Al arrancar, el KafkaAdmin de Spring busca los beans NewTopic y crea los que no existen
 * con esta configuración (si ya existen, no los modifica).
 */
@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaTopicConfig {

    /**
     * reservations.events: fuente de verdad de lo que le pasa a una reserva.
     * cleanup.policy=delete -> los eventos se borran al vencer la retención.
     * min.insync.replicas=2 -> con acks=all, una escritura se confirma cuando 2 de 3 réplicas la tienen.
     */
    @Bean
    public NewTopic reservationEventsTopic(KafkaTopicsProperties props) {
        KafkaTopicsProperties.TopicSpec topic = props.reservationEvents();
        return TopicBuilder.name(topic.name())
                .partitions(topic.partitions())
                .replicas(topic.replicas())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(topic.retention().toMillis()))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(topic.minInsyncReplicas()))
                .build();
    }
}
