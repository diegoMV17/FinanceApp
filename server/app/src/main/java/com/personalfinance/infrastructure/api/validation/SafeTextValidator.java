package com.personalfinance.infrastructure.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * La única definición de qué carácter no entra.
 *
 * <p>Un solo sitio, para que no se pueda ir separando de la lista que se
 * documenta en {@link SafeText}.
 */
public final class SafeTextValidator implements ConstraintValidator<SafeText, String> {

    /** Separador de línea. No es {@code \n} y rompe una línea igual. */
    private static final int LINE_SEPARATOR = 0x2028;

    /** Separador de párrafo. */
    private static final int PARAGRAPH_SEPARATOR = 0x2029;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Que falte no es asunto de esta restricción: eso lo dice @NotNull, y
        // así un campo opcional puede no venir sin que esto proteste.
        if (value == null) {
            return true;
        }
        return value.codePoints().noneMatch(SafeTextValidator::isForbidden);
    }

    private static boolean isForbidden(int codePoint) {
        return Character.isISOControl(codePoint)
                // Cf: la categoría de los invisibles de formato. Ahí viven los
                // controles de dirección (U+202A-202E, U+2066-2069), las marcas
                // de izquierda a derecha, los anchos cero y el U+FEFF.
                || Character.getType(codePoint) == Character.FORMAT
                || codePoint == LINE_SEPARATOR
                || codePoint == PARAGRAPH_SEPARATOR;
    }
}
