package com.andesstay.ms_andesstay_reservations.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuracion del productor:
 * - Mensajes en JSON (Jackson 3).
 * - Publisher confirms (correlated): el broker confirma (ack/nack) cada mensaje publicado.
 * - Publisher returns + mandatory=true: si ninguna cola calza con la routing key, el broker
 *   devuelve el mensaje en lugar de descartarlo en silencio.
 * Las propiedades spring.rabbitmq.publisher-confirm-type, publisher-returns y template.mandatory
 * estan en application.yaml.
 */
@Configuration
public class RabbitMessagingConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMessagingConfig.class);

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    public RabbitTemplateCustomizer publisherCallbacksCustomizer() {
        return template -> {
            template.setConfirmCallback((correlationData, ack, cause) -> {
                String id = correlationData != null ? correlationData.getId() : "sin-correlacion";
                if (ack) {
                    log.info("[CONFIRM] broker confirmo eventId={}", id);
                } else {
                    log.error("[CONFIRM] broker rechazo (nack) eventId={} causa={}", id, cause);
                }
            });
            template.setReturnsCallback(returned -> log.error(
                    "[RETURN] mensaje no enrutable messageId={} exchange={} routingKey={} replyCode={} replyText={}",
                    returned.getMessage().getMessageProperties().getMessageId(),
                    returned.getExchange(), returned.getRoutingKey(),
                    returned.getReplyCode(), returned.getReplyText()));
        };
    }
}
