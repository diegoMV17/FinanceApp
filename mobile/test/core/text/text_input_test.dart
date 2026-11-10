import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/text/text_input.dart';

/// El formato del texto que se escribe: sin espacios de los lados y dentro del
/// tope del servidor. Lo demás (caracteres invisibles, de control) lo rechaza
/// el servidor, y la app muestra su respuesta.
void main() {
  Matcher fails(TextProblem problem) => throwsA(
    isA<TextInputException>().having(
      (TextInputException e) => e.problem,
      'problem',
      problem,
    ),
  );

  group('un nombre', () {
    test('se envía sin los espacios de los lados', () {
      expect(checkedName('  Nequi '), 'Nequi');
    });

    test('no puede quedar en blanco', () {
      expect(() => checkedName(''), fails(TextProblem.blank));
      expect(() => checkedName('   '), fails(TextProblem.blank));
    });

    test('hasta el tope, contado como lo cuenta el borde del servidor', () {
      expect(checkedName('a' * maxNameLength), hasLength(maxNameLength));
      expect(
        () => checkedName('a' * (maxNameLength + 1)),
        fails(TextProblem.tooLong),
      );
      // Un emoji son dos unidades UTF-16, y así cuenta @Size en Java.
      expect(
        () => checkedName('😀' * (maxNameLength ~/ 2 + 1)),
        fails(TextProblem.tooLong),
      );
    });

    test('el tope se mide sin los espacios de los lados', () {
      expect(checkedName(' ${'a' * maxNameLength} '), hasLength(maxNameLength));
    });
  });

  group('una descripción', () {
    test('puede ir vacía', () {
      expect(checkedDescription('   '), '');
    });

    test('hasta su tope', () {
      expect(
        checkedDescription('a' * maxDescriptionLength),
        hasLength(maxDescriptionLength),
      );
      expect(
        () => checkedDescription('a' * (maxDescriptionLength + 1)),
        fails(TextProblem.tooLong),
      );
    });
  });
}
