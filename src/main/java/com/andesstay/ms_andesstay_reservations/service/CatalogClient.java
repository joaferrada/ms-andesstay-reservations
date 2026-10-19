package com.andesstay.ms_andesstay_reservations.service;

import com.andesstay.ms_andesstay_reservations.exception.CatalogConflictException;
import com.andesstay.ms_andesstay_reservations.exception.CatalogUnavailableException;

/**
 * Puerto de salida hacia ms-andesstay-catalog (cupos de las unidades).
 * La implementacion REST vive en el paquete {@code client}.
 */
public interface CatalogClient {

    /**
     * Descuenta un cupo de la unidad (al confirmar).
     *
     * @throws CatalogConflictException    409: la unidad no tiene cupos, esta inactiva o no existe
     * @throws CatalogUnavailableException 503: catalog no responde
     */
    void reservarCupo(Long unidadId);

    /**
     * Devuelve un cupo (al cancelar o hacer checkout). No lanza excepciones: si catalog no
     * responde se registra un warning y la operacion sigue.
     */
    void liberarCupo(Long unidadId);
}
