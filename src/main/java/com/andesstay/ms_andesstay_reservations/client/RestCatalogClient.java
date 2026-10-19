package com.andesstay.ms_andesstay_reservations.client;

import com.andesstay.ms_andesstay_reservations.exception.CatalogConflictException;
import com.andesstay.ms_andesstay_reservations.exception.CatalogUnavailableException;
import com.andesstay.ms_andesstay_reservations.service.CatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Adaptador REST de {@link CatalogClient}: POST /api/catalog/units/{id}/reserve y /release.
 */
@Component
public class RestCatalogClient implements CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(RestCatalogClient.class);
    private static final String PREFIJO = "No se puede confirmar la reserva: ";

    private final RestClient restClient;

    public RestCatalogClient(@Qualifier("catalogRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void reservarCupo(Long unidadId) {
        try {
            restClient.post().uri("/api/catalog/units/{id}/reserve", unidadId).retrieve().toBodilessEntity();
            log.info("[CATALOG] cupo descontado unidadId={}", unidadId);
        } catch (HttpClientErrorException.Conflict ex) {
            throw new CatalogConflictException(PREFIJO + mensajeDe(ex, "la unidad " + unidadId + " no tiene cupos disponibles"));
        } catch (HttpClientErrorException.NotFound ex) {
            throw new CatalogConflictException(PREFIJO + "la unidad " + unidadId + " no existe en el catalogo");
        } catch (HttpClientErrorException ex) {
            throw new CatalogUnavailableException(PREFIJO + "ms-andesstay-catalog rechazo la operacion ("
                    + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            log.warn("[CATALOG] no responde al reservar unidadId={}: {}", unidadId, ex.getMessage());
            throw new CatalogUnavailableException(PREFIJO + "ms-andesstay-catalog no responde. Intente mas tarde", ex);
        }
    }

    @Override
    public void liberarCupo(Long unidadId) {
        try {
            restClient.post().uri("/api/catalog/units/{id}/release", unidadId).retrieve().toBodilessEntity();
            log.info("[CATALOG] cupo liberado unidadId={}", unidadId);
        } catch (RestClientException ex) {
            // La cancelacion/checkout no se bloquea; el cupo queda pendiente de devolver a mano.
            log.warn("[CATALOG] no se pudo liberar el cupo de unidadId={}: {}", unidadId, ex.getMessage());
        }
    }

    /** Usa el "message" del JSON de error de catalog si viene. */
    private static String mensajeDe(HttpClientErrorException ex, String porDefecto) {
        try {
            Map<?, ?> body = ex.getResponseBodyAs(Map.class);
            Object message = body != null ? body.get("message") : null;
            return message != null ? message.toString() : porDefecto;
        } catch (RuntimeException ignorada) {
            return porDefecto;
        }
    }
}
