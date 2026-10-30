package com.personalfinance.application.port;

/**
 * La bitácora de lo que la aplicación hizo, append-only.
 *
 * <p>Es distinta de los logs y distinta de la auditoría que el propio libro ya
 * lleva ({@code cancelled_at}, {@code replaces}, nada se borra). Lo que agrega
 * es quién pidió qué y cuándo: cuando el bot interprete mal un mensaje, esta es
 * la tabla que dice de dónde salió ese movimiento raro.
 *
 * <p>Se escribe dentro de la misma transacción del caso de uso: una fila por
 * cada cambio que de verdad ocurrió, ninguna por un reintento que no escribió
 * nada y ninguna por un fallo.
 */
public interface AuditTrail {

    void record(AuditRecord record);
}
