package com.personalfinance.infrastructure.config;

import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * En qué calendario vive el dueño del libro.
 *
 * <p>Sirve para una sola cosa: decidir qué día es hoy cuando una lectura no
 * trae fecha. Los instantes que se guardan — {@code created_at},
 * {@code archived_at}, {@code cancelled_at}, la bitácora — siguen naciendo en
 * UTC del reloj de la aplicación, y esta zona no los toca. Un instante es el
 * mismo hecho en cualquier zona; una fecha de calendario no.
 *
 * <p>Hacía falta porque el reloj es UTC y Colombia está en UTC−5: a las siete
 * de la tarde en Bogotá, para UTC ya es mañana. El último día del mes eso
 * mandaba «ingresos y gastos del mes» al mes siguiente, en cero, justo la noche
 * en que se mira el cierre; y el resumen de «hoy» contestaba por un día que
 * todavía no había empezado.
 *
 * <p>Es configurable y no fija porque el dueño puede viajar o mudarse, y porque
 * una constante escondida en el código es la clase de cosa que nadie encuentra
 * cuando las fechas empiezan a salir corridas por un día.
 *
 * <p>El valor por omisión vive aquí y no en {@code application.yml}, como el
 * del candado: una sola fuente de verdad, y la aplicación arranca igual sin
 * configuración. Se valida al construirse, así que una zona inventada impide el
 * arranque en vez de aparecer como fechas raras meses después.
 */
@ConfigurationProperties("finance")
public record TimeZoneProperties(@DefaultValue(DEFAULT_ZONE) String timezone) {

    /** Donde vive el dueño. */
    static final String DEFAULT_ZONE = "America/Bogota";

    public TimeZoneProperties {
        // Ausente y en blanco no son lo mismo. Ausente es "no lo configuré", y
        // ahí el valor por omisión es la respuesta correcta. En blanco es la
        // clave escrita y dejada vacía, que es un dedazo: nada no es una zona
        // horaria, y arrancar con Bogotá porque alguien borró el valor a medias
        // es justo el arranque silencioso que este repositorio no quiere.
        if (timezone == null) {
            timezone = DEFAULT_ZONE;
        }
        if (timezone.isBlank()) {
            throw new IllegalArgumentException("finance.timezone no puede estar en blanco");
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException unknown) {
            throw new IllegalArgumentException(
                    "finance.timezone no es una zona horaria conocida: " + timezone, unknown);
        }
    }

    /** La zona ya validada. */
    public ZoneId zone() {
        return ZoneId.of(timezone);
    }
}
