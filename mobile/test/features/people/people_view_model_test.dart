import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/forms/submit_state.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/ledger/book_changes.dart';
import 'package:mobile/features/people/ui/people_view_model.dart';

import '../../support/app_container.dart';
import '../../support/server_fixture.dart';

void main() {
  const String juan = '0a3c0f5e-2b1d-4c8e-9f00-000000000001';
  const String loan = '0a3c0f5e-2b1d-4c8e-9f00-000000000002';

  late ServerFixture server;
  late ProviderContainer container;

  Map<String, Object?> echo(Object? body) {
    final Map<String, Object?> sent = body! as Map<String, Object?>;
    return <String, Object?>{
      'id': sent['id'],
      'name': sent['name'],
      'type': ?sent['type'],
    };
  }

  setUp(() async {
    server = ServerFixture();
    container = appContainer(server);
    await unlock(container);
  });

  tearDown(() async {
    container.dispose();
    await server.close();
  });

  test('la lista: bloqueada nada, desbloqueada se lee', () async {
    server.answer('GET /api/people', 200, <String, Object?>{
      'asOf': '2026-11-09',
      'people': <Object?>[],
    });
    container.listen(peopleViewModelProvider, (_, _) {});

    expect(await container.read(peopleViewModelProvider.future), isNotNull);
  });

  group('registrar a alguien', () {
    late ProviderSubscription<SubmitState> form;

    setUp(() {
      form = container.listen(newPersonViewModelProvider, (_, _) {});
    });

    Future<void> submit(String name) =>
        container.read(newPersonViewModelProvider.notifier).submit(name);

    test('sin nombre no sale', () async {
      await submit('');

      expect(form.read().error, isNotNull);
      expect(server.adapter.requests, isEmpty);
    });

    test('registrado, el libro cambió', () async {
      server.routes['POST /api/people'] = (o) => (201, echo(o.data));

      await submit('Juan');

      expect(form.read().isDone, isTrue);
      expect(container.read(bookChangesProvider), 1);
    });

    test('un reintento manda el mismo id', () async {
      server.offline = true;
      await submit('Juan');
      server
        ..offline = false
        ..routes['POST /api/people'] = (o) => (200, echo(o.data));
      await submit('Juan');

      final List<Object?> sent = server.sent('POST /api/people');
      expect(
        (sent[0]! as Map<String, Object?>)['id'],
        (sent[1]! as Map<String, Object?>)['id'],
      );
    });
  });

  group('la cuenta de alguien', () {
    late ProviderSubscription<SubmitState> form;

    setUp(() {
      form = container.listen(personAccountViewModelProvider(juan), (_, _) {});
    });

    test('vincular una que existe', () async {
      server.answer('POST /api/people/$juan/accounts', 201, <String, Object?>{
        'id': loan,
        'name': 'Préstamo',
        'type': 'RECEIVABLE',
      });

      await container
          .read(personAccountViewModelProvider(juan).notifier)
          .linkExisting(loan);

      expect(form.read().isDone, isTrue);
      expect(
        server.sent('POST /api/people/$juan/accounts').single,
        <String, Object?>{'account': loan},
      );
    });

    test('abrir una nueva: si vincular falla, reintentar repite el mismo id y '
        'no abre otra', () async {
      server.routes['POST /api/accounts'] = (o) => (201, echo(o.data));
      server.answer(
        'POST /api/people/$juan/accounts',
        503,
        apiProblem('book-busy', 503),
      );
      Future<void> open() => container
          .read(personAccountViewModelProvider(juan).notifier)
          .openNew(name: 'Préstamo a Juan', kind: AccountKind.receivable);

      await open();
      expect(form.read().phase, SubmitPhase.editing);

      server.routes['POST /api/accounts'] = (o) => (200, echo(o.data));
      server.routes['POST /api/people/$juan/accounts'] = (o) => (
        201,
        <String, Object?>{
          'id': (o.data! as Map<String, Object?>)['account'],
          'name': 'Préstamo a Juan',
          'type': 'RECEIVABLE',
        },
      );
      await open();

      final List<Object?> opened = server.sent('POST /api/accounts');
      expect(opened, hasLength(2));
      expect(
        (opened[0]! as Map<String, Object?>)['id'],
        (opened[1]! as Map<String, Object?>)['id'],
      );
      expect(form.read().isDone, isTrue);
    });
  });
}
