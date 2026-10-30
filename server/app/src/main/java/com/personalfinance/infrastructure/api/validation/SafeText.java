package com.personalfinance.infrastructure.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Texto que puede acabar en una pantalla sin hacer trampas.
 *
 * <p>El dominio ya rechaza los caracteres de control, y lo hace en el sitio
 * correcto: {@code Text.within} es la última línea de defensa y su javadoc dice
 * en voz alta que quien alimenta el dominio limpia su propia entrada. Lo que el
 * dominio <em>no</em> ve es todo lo que {@code Character.isISOControl} no cuenta
 * como de control y sin embargo es invisible:
 *
 * <ul>
 *   <li>Los controles de dirección — U+202E y compañía, U+2066 a U+2069 — que
 *       dan la vuelta al texto de una línea. Una descripción con uno de ellos se
 *       lee al revés en el chat del bot y en cualquier lista, y lo que el dueño
 *       ve no es lo que está guardado.
 *   <li>U+2028 y U+2029, separadores de línea y de párrafo, que rompen una
 *       línea sin ser {@code \n}.
 *   <li>Los demás invisibles de formato: anchos cero, marcas de orden de bytes.
 * </ul>
 *
 * <p>Esto no duplica la regla del dominio, la extiende: el dominio sigue
 * rechazando lo suyo, y esto rechaza lo que el dominio no puede ver.
 *
 * <p>La API <strong>rechaza y no limpia</strong>, y esa es la diferencia con el
 * bot. Al bot le escribe una persona que no va a volver a redactar el mensaje,
 * así que ahí se limpia; a la API le escribe un programa, y a un programa hay
 * que decirle que mandó basura para que la arregle en su lado.
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {

    String message() default "trae caracteres que no se admiten";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
