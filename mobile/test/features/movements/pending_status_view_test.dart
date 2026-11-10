import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/features/movements/data/pending_movements_repository.dart';
import 'package:mobile/features/movements/ui/pending_status_view.dart';

/// Los pendientes se ven siempre, con su número.
void main() {
  Future<void> pump(WidgetTester tester, PendingSummary summary) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          pendingSummaryProvider.overrideWith(
            (Ref ref) => Stream<PendingSummary>.value(summary),
          ),
        ],
        child: const MaterialApp(home: Scaffold(body: PendingStatusView())),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('sin nada, lo dice', (WidgetTester tester) async {
    await pump(tester, const PendingSummary(0, 0, null));

    expect(find.text('Sin pendientes'), findsOneWidget);
  });

  testWidgets('uno, en singular', (WidgetTester tester) async {
    await pump(tester, const PendingSummary(1, 0, null));

    expect(find.text('1 pendiente de enviar'), findsOneWidget);
    expect(find.text('Sin pendientes'), findsNothing);
  });

  testWidgets('varios, con su número', (WidgetTester tester) async {
    await pump(tester, const PendingSummary(3, 0, null));

    expect(find.text('3 pendientes de enviar'), findsOneWidget);
  });

  testWidgets('lo que necesita atención se ve aparte', (
    WidgetTester tester,
  ) async {
    await pump(tester, const PendingSummary(2, 1, null));

    expect(find.text('2 pendientes de enviar'), findsOneWidget);
    expect(find.text('1 necesita atención'), findsOneWidget);
  });

  testWidgets('varios que necesitan atención, en plural', (
    WidgetTester tester,
  ) async {
    await pump(tester, const PendingSummary(0, 2, null));

    expect(find.text('2 necesitan atención'), findsOneWidget);
    expect(find.text('Sin pendientes'), findsNothing);
  });

  group('dice por qué no salen, y que no se perdieron', () {
    for (final (SendingProblem problem, String words)
        in <(SendingProblem, String)>[
          (SendingProblem.token, 'token'),
          (SendingProblem.tooManyAttempts, 'intentos'),
          (SendingProblem.serverBusy, 'ocupado'),
          (SendingProblem.noConnection, 'conexión'),
          (SendingProblem.unexpected, 'inesperado'),
        ]) {
      testWidgets(problem.name, (WidgetTester tester) async {
        await pump(tester, PendingSummary(2, 0, problem));

        expect(find.textContaining(words), findsOneWidget);
        expect(find.textContaining('guardados'), findsOneWidget);
      });
    }
  });

  testWidgets('sin nada que enviar, una pausa vieja no asusta a nadie', (
    WidgetTester tester,
  ) async {
    await pump(tester, const PendingSummary(0, 0, SendingProblem.noConnection));

    expect(find.text('Sin pendientes'), findsOneWidget);
    expect(find.textContaining('conexión'), findsNothing);
  });
}
