import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/forms/submit_state.dart';
import 'package:mobile/core/ledger/account_ref.dart';
import 'package:mobile/core/ledger/book_changes.dart';
import 'package:mobile/core/lock/app_lock.dart';
import 'package:mobile/core/reading/reading.dart';
import 'package:mobile/features/accounts/ui/accounts_view_model.dart';

import '../../support/app_container.dart';
import '../../support/server_fixture.dart';

/// Sin widgets: las reglas de la pantalla de cuentas, con el bloqueo, la
/// base y la puerta de la app de verdad sobre un servidor falso.
void main() {
  late ServerFixture server;
  late ProviderContainer container;

  Map<String, Object?> opened(Object? body) {
    final Map<String, Object?> sent = body! as Map<String, Object?>;
    return <String, Object?>{
      'id': sent['id'],
      'name': sent['name'],
      'type': sent['type'],
    };
  }

  void serverHasNoAccounts() => server.answer(
    'GET /api/balances',
    200,
    <String, Object?>{'asOf': '2026-11-09', 'accounts': <Object?>[]},
  );

  setUp(() {
    server = ServerFixture();
    container = appContainer(server);
  });

  tearDown(() async {
    container.dispose();
    await server.close();
  });

  group('la lista', () {
    setUp(() {
      container.listen(accountsViewModelProvider, (_, _) {});
      serverHasNoAccounts();
    });

    test('bloqueada no se lee nada', () async {
      expect(await container.read(accountsViewModelProvider.future), isNull);
      expect(server.adapter.requests, isEmpty);
    });

    test('al desbloquear, se lee', () async {
      await unlock(container);

      final Reading<List<AccountRef>>? reading = await container.read(
        accountsViewModelProvider.future,
      );

      expect(reading!.value, isEmpty);
      expect(server.adapter.requests, hasLength(1));
    });

    test('al bloquearse otra vez, no vuelve a salir', () async {
      await unlock(container);
      await container.read(accountsViewModelProvider.future);
      container
          .read(appLockProvider.notifier)
          .onLifecycle(AppLifecycleState.paused);

      expect(await container.read(accountsViewModelProvider.future), isNull);
      expect(server.adapter.requests, hasLength(1));
    });

    test('cuando el libro cambia, se vuelve a pedir', () async {
      await unlock(container);
      await container.read(accountsViewModelProvider.future);

      container.read(bookChangesProvider.notifier).changed();
      await container.read(accountsViewModelProvider.future);

      expect(server.adapter.requests, hasLength(2));
    });
  });

  group('abrir una', () {
    late ProviderSubscription<SubmitState> form;

    setUp(() async {
      await unlock(container);
      form = container.listen(openAccountViewModelProvider, (_, _) {});
    });

    Future<void> submit(String name) => container
        .read(openAccountViewModelProvider.notifier)
        .submit(name: name, kind: AccountKind.asset);

    test('un nombre en blanco ni sale', () async {
      await submit('  ');

      expect(form.read().error, isNotNull);
      expect(server.adapter.requests, isEmpty);
    });

    test('abierta, avisa que el libro cambió', () async {
      server.routes['POST /api/accounts'] = (o) => (201, opened(o.data));

      await submit('Nequi');

      expect(form.read().isDone, isTrue);
      expect(container.read(bookChangesProvider), 1);
    });

    test('reintentar después de un fallo manda el mismo id', () async {
      server.offline = true;
      await submit('Nequi');
      expect(form.read().phase, SubmitPhase.editing);
      expect(form.read().error, contains('Tailscale'));

      server
        ..offline = false
        ..routes['POST /api/accounts'] = (o) => (200, opened(o.data));
      await submit('Nequi');

      final List<Object?> sent = server.sent('POST /api/accounts');
      expect(sent, hasLength(2));
      expect(
        (sent[0]! as Map<String, Object?>)['id'],
        (sent[1]! as Map<String, Object?>)['id'],
      );
      expect(form.read().isDone, isTrue);
    });

    test('un doble toque manda una sola vez', () async {
      server.routes['POST /api/accounts'] = (o) => (201, opened(o.data));

      await Future.wait(<Future<void>>[submit('Nequi'), submit('Nequi')]);

      expect(server.sent('POST /api/accounts'), hasLength(1));
    });

    test(
      'el rechazo del servidor se dice con su mensaje, no su texto',
      () async {
        server.answer(
          'POST /api/accounts',
          409,
          apiProblem('duplicate-account-name', 409),
        );

        await submit('Nequi');

        expect(form.read().error, contains('nombre'));
        expect(form.read().error, isNot(contains('Texto fijo')));
        expect(container.read(bookChangesProvider), 0);
      },
    );

    test('otro formulario es otra cuenta: otro id', () async {
      server.routes['POST /api/accounts'] = (o) => (201, opened(o.data));
      await submit('Nequi');
      // Cerrar la pantalla libera el formulario; abrirla otra vez es otro.
      form.close();
      await container.pump();
      form = container.listen(openAccountViewModelProvider, (_, _) {});

      await submit('Bancolombia');

      final List<Object?> sent = server.sent('POST /api/accounts');
      expect(
        (sent[0]! as Map<String, Object?>)['id'],
        isNot((sent[1]! as Map<String, Object?>)['id']),
      );
    });
  });
}
