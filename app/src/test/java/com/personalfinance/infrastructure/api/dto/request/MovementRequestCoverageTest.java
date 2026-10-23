package com.personalfinance.infrastructure.api.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.personalfinance.application.command.MovementCommand;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.shared.Money;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * H1. Que los once tipos de movimiento sigan siendo los mismos once en todas
 * partes.
 *
 * <p>Hay tres listas de once que tienen que decir lo mismo: los
 * {@code permits} de {@link MovementRequest}, los {@code @JsonSubTypes} de esa
 * misma interfaz, y los {@code permits} de {@link MovementCommand}. El
 * compilador vigila la primera y la tercera. La de Jackson no la vigila nadie,
 * y ahi esta el fallo que este test existe para evitar: un movimiento nuevo
 * compila perfectamente, pasa todos los tests de su propia clase, y es
 * inalcanzable por HTTP porque nadie lo anadio a la anotacion.
 *
 * <p>Y una cuarta cosa, la mas facil de romper sin darse cuenta: que cada
 * cuenta llegue al hueco que le corresponde. Un {@code paidFrom} que acabe en
 * {@code category} construye un asiento perfectamente balanceado, pasa todas
 * las reglas del dominio, y guarda el gasto al reves. Por eso la comprobacion
 * no es posicional sino por nombre: una misma palabra para una misma cosa en
 * las dos capas.
 */
@DisplayName("Los once tipos de movimiento")
class MovementRequestCoverageTest {

    private static final int HOW_MANY = 11;

    @Test
    @DisplayName("H1 · la interfaz sella exactamente once")
    void thereAreElevenSealedTypes() {
        assertThat(MovementRequest.class.getPermittedSubclasses()).hasSize(HOW_MANY);
        assertThat(MovementCommand.class.getPermittedSubclasses()).hasSize(HOW_MANY);
    }

    @Test
    @DisplayName("H1 · y Jackson conoce esos mismos once")
    void jacksonKnowsTheSameEleven() {
        JsonSubTypes declared = MovementRequest.class.getAnnotation(JsonSubTypes.class);
        assertThat(declared).as("la interfaz declara sus subtipos").isNotNull();

        Set<Class<?>> registered = Arrays.stream(declared.value())
                .map(JsonSubTypes.Type::value)
                .collect(Collectors.toSet());

        assertThat(registered)
                .as("ni uno de mas ni uno de menos que los sellados")
                .containsExactlyInAnyOrder(MovementRequest.class.getPermittedSubclasses());
    }

    @Test
    @DisplayName("H1 · con un nombre distinto y no vacio cada uno")
    void eachNameIsItsOwn() {
        JsonSubTypes declared = MovementRequest.class.getAnnotation(JsonSubTypes.class);

        List<String> names = Arrays.stream(declared.value())
                .map(JsonSubTypes.Type::name)
                .toList();

        assertThat(names).hasSize(HOW_MANY).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(names).allSatisfy(name -> assertThat(name).isNotBlank());
    }

    @Test
    @DisplayName("H1 · cada peticion construye un comando distinto, y los cubre todos")
    void everyRequestBuildsItsOwnCommand() {
        Set<Class<?>> built = Arrays.stream(MovementRequest.class.getPermittedSubclasses())
                .map(MovementRequestCoverageTest::sampleOf)
                .map(MovementRequest::toCommand)
                .map(Object::getClass)
                .collect(Collectors.toSet());

        assertThat(built)
                .as("los once comandos sellados, sin repetir ninguno")
                .containsExactlyInAnyOrder(MovementCommand.class.getPermittedSubclasses());
    }

    @Test
    @DisplayName("H1 · y cada cuenta llega al hueco de su propio nombre")
    void everyAccountLandsWhereItsNameSays() {
        for (Class<?> type : MovementRequest.class.getPermittedSubclasses()) {
            Map<String, UUID> sent = new HashMap<>();
            MovementRequest request = sampleOf(type, sent);
            MovementCommand command = request.toCommand();

            for (RecordComponent component : command.getClass().getRecordComponents()) {
                Object value = read(component, command);
                if (!(value instanceof AccountId account)) {
                    continue;
                }
                assertThat(sent)
                        .as("%s declara una cuenta '%s' que la peticion no manda",
                                command.getClass().getSimpleName(), component.getName())
                        .containsKey(component.getName());

                assertThat(account.value())
                        .as("%s: la cuenta '%s' no es la que mando la peticion",
                                command.getClass().getSimpleName(), component.getName())
                        .isEqualTo(sent.get(component.getName()));
            }
        }
    }

    @Test
    @DisplayName("H1 · el id, la fecha y el monto tampoco se cruzan")
    void theIdDateAndAmountSurviveTheTrip() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate date = LocalDate.of(2026, 10, 20);
        long cents = 123_456L;

        ExpenseRequest expense = new ExpenseRequest(
                id, date, "Almuerzo",
                UUID.randomUUID(), UUID.randomUUID(), cents);

        MovementCommand command = expense.toCommand();

        assertThat(command.id()).isEqualTo(new TransactionId(id));
        assertThat(valueNamed(command, "date")).isEqualTo(date);
        assertThat(valueNamed(command, "description")).isEqualTo("Almuerzo");
        assertThat(valueNamed(command, "amount")).isEqualTo(Money.ofCents(cents));
    }

    private static MovementRequest sampleOf(Class<?> type) {
        return sampleOf(type, new HashMap<>());
    }

    /**
     * Construye la peticion por reflexion, con un UUID distinto por cada
     * componente de cuenta y apuntando cual fue a cada nombre.
     */
    private static MovementRequest sampleOf(Class<?> type, Map<String, UUID> accountsSent) {
        RecordComponent[] components = type.getRecordComponents();
        List<Object> arguments = new ArrayList<>();

        for (RecordComponent component : components) {
            String name = component.getName();
            Class<?> kind = component.getType();

            if (kind == UUID.class && name.equals("id")) {
                arguments.add(UUID.randomUUID());
            } else if (kind == UUID.class) {
                UUID account = UUID.randomUUID();
                accountsSent.put(name, account);
                arguments.add(account);
            } else if (kind == LocalDate.class) {
                arguments.add(LocalDate.of(2026, 10, 20));
            } else if (kind == String.class) {
                arguments.add("Almuerzo");
            } else if (kind == long.class) {
                arguments.add(1_000L);
            } else {
                throw new AssertionError(
                        "componente inesperado en " + type.getSimpleName() + ": " + name
                                + " de tipo " + kind + ". Si se anadio un campo nuevo a las "
                                + "peticiones de movimiento, este test tiene que saber "
                                + "construirlo.");
            }
        }
        try {
            Class<?>[] kinds = Arrays.stream(components)
                    .map(RecordComponent::getType)
                    .toArray(Class<?>[]::new);
            Constructor<?> canonical = type.getDeclaredConstructor(kinds);
            canonical.setAccessible(true);
            return (MovementRequest) canonical.newInstance(arguments.toArray());
        } catch (ReflectiveOperationException cannot) {
            throw new AssertionError("no se pudo construir " + type.getSimpleName(), cannot);
        }
    }

    private static Object valueNamed(MovementCommand command, String name) {
        for (RecordComponent component : command.getClass().getRecordComponents()) {
            if (component.getName().equals(name)) {
                return read(component, command);
            }
        }
        throw new AssertionError(
                command.getClass().getSimpleName() + " no tiene un componente " + name);
    }

    private static Object read(RecordComponent component, Object from) {
        try {
            return component.getAccessor().invoke(from);
        } catch (ReflectiveOperationException cannot) {
            throw new AssertionError("no se pudo leer " + component.getName(), cannot);
        }
    }
}
