import 'package:flutter_test/flutter_test.dart';

import 'architecture/import_rules.dart';

/// El ArchUnit del servidor, en Dart.
///
/// Las reglas se prueban contra archivos inventados —así se ve que de verdad
/// fallan— y después se corren sobre el `lib/` de verdad.
void main() {
  group('una funcionalidad no importa el interior de otra', () {
    test('dos archivos de la misma funcionalidad pueden verse', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile('features/people/ui/people_screen.dart', <String>[
          'features/people/data/people_repository.dart',
        ]),
      ]);

      expect(found, isEmpty);
    });

    test('un archivo de otra funcionalidad, no', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile('features/dashboard/ui/dashboard_screen.dart', <String>[
          'features/budgets/data/budgets_repository.dart',
        ]),
      ]);

      expect(found, hasLength(1));
      expect(found.single.rule, Rule.featureReachesIntoAnother);
    });

    test('lo compartido vive en core/ o en data/, y de ahí sí se importa', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile(
          'features/movements/data/movements_repository.dart',
          <String>['core/money/money.dart', 'data/sync/pending_queue.dart'],
        ),
      ]);

      expect(found, isEmpty);
    });
  });

  group('un widget no habla con la API ni con la base', () {
    test('la interfaz de una funcionalidad no importa data/local', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile('features/accounts/ui/accounts_screen.dart', <String>[
          'data/local/app_database.dart',
        ]),
      ]);

      expect(found, hasLength(1));
      expect(found.single.rule, Rule.uiTalksToData);
    });

    test('la interfaz le pide a su repositorio', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile(
          'features/accounts/ui/accounts_view_model.dart',
          <String>['features/accounts/data/accounts_repository.dart'],
        ),
      ]);

      expect(found, isEmpty);
    });

    test('el repositorio de la funcionalidad sí', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile(
          'features/accounts/data/accounts_repository.dart',
          <String>['data/local/app_database.dart'],
        ),
      ]);

      expect(found, isEmpty);
    });
  });

  group('el cliente generado está envuelto', () {
    test('nadie importa data/api fuera de core/network y data/', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile('features/people/data/people_repository.dart', <String>[
          'data/api/people_api.dart',
        ]),
      ]);

      expect(found, hasLength(1));
      expect(found.single.rule, Rule.generatedClientLeaks);
    });

    test('core/network y data/sync sí', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile('core/network/api_client.dart', <String>[
          'data/api/people_api.dart',
        ]),
        const DartFile('data/sync/sender.dart', <String>[
          'data/api/movements_api.dart',
        ]),
      ]);

      expect(found, isEmpty);
    });
  });

  group('core/ no conoce a quien lo usa', () {
    test('core no importa features ni data', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile('core/money/money_format.dart', <String>[
          'features/budgets/ui/budgets_screen.dart',
        ]),
        const DartFile('core/errors/messages.dart', <String>[
          'data/local/app_database.dart',
        ]),
      ]);

      expect(found, hasLength(2));
      expect(
        found.map((Violation v) => v.rule),
        everyElement(Rule.coreKnowsItsCallers),
      );
    });

    test('core/network es la excepción: envuelve el cliente generado', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile('core/network/problem_detail.dart', <String>[
          'data/api/models.dart',
        ]),
      ]);

      expect(found, isEmpty);
    });
  });

  group('las reglas no se aplican a los paquetes de terceros', () {
    test('importar Flutter o un paquete de pub nunca es una violación', () {
      final List<Violation> found = violations(<DartFile>[
        const DartFile('features/people/ui/people_screen.dart', <String>[
          'package:flutter/material.dart',
          'package:flutter_riverpod/flutter_riverpod.dart',
          'dart:async',
        ]),
      ]);

      expect(found, isEmpty);
    });
  });

  group('el lib/ de verdad', () {
    test('no tiene ninguna violación', () {
      final List<DartFile> files = libraryFiles();

      expect(files, isNotEmpty, reason: 'lib/ no se pudo leer');
      expect(
        violations(files).map((Violation v) => v.toString()).toList(),
        isEmpty,
      );
    });

    test('los imports relativos se resuelven como rutas de lib/', () {
      final DartFile main = libraryFiles().firstWhere(
        (DartFile file) => file.path == 'main.dart',
      );

      expect(main.imports, contains('app.dart'));
    });
  });
}
