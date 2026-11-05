import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/data/api/export.dart';

/// El cliente generado, contra un transporte falso: sin red y sin servidor.
///
/// Lo que se prueba es lo que el generador podría hacer mal sin que nadie lo
/// note hasta hablar con el servidor de verdad: la ruta, el cuerpo del oneOf,
/// las fechas y que 201 y 200 sean los dos éxito.
void main() {
  // Una dirección que no existe a propósito (.invalid está reservado): la de
  // verdad es privada y no va en el repositorio.
  const String baseUrl = 'https://api.invalid';

  late _RecordingAdapter adapter;
  late FinanceApi api;

  setUp(() {
    adapter = _RecordingAdapter();
    api = FinanceApi(
      Dio(BaseOptions(baseUrl: baseUrl))..httpClientAdapter = adapter,
    );
  });

  const MovementRequest expense = MovementRequestExpenseRequest(
    type: ExpenseRequestType.expense,
    id: '0a3c0f5e-2b1d-4c8e-9f00-000000000001',
    date: '2026-11-02',
    description: 'Almuerzo',
    paidFrom: '0a3c0f5e-2b1d-4c8e-9f00-000000000002',
    category: '0a3c0f5e-2b1d-4c8e-9f00-000000000003',
    amountInCents: 1234,
  );

  test(
    'registrar un gasto: POST /api/movements con el JSON del gasto',
    () async {
      adapter.answer(201, _movement);

      await api.movements.movementsRecord(body: expense);

      expect(adapter.request.method, 'POST');
      expect(adapter.request.uri.toString(), '$baseUrl/api/movements');
      expect(adapter.sentJson, expense.toJson());
      expect((adapter.sentJson! as Map<String, Object?>)['type'], 'EXPENSE');
    },
  );

  test(
    '201 (aplicado) y 200 (ya estaba) se leen igual: los dos son éxito',
    () async {
      for (final int status in <int>[201, 200]) {
        adapter.answer(status, _movement);

        final MovementResponse answer = await api.movements.movementsRecord(
          body: expense,
        );

        expect(answer.id, '0a3c0f5e-2b1d-4c8e-9f00-000000000001');
        expect(answer.date, '2026-11-02');
      }
    },
  );

  test(
    'una corrección lleva el movimiento entero dentro de correction',
    () async {
      adapter.answer(201, _movement);

      await api.movements.movementsAmend(
        id: '0a3c0f5e-2b1d-4c8e-9f00-000000000009',
        body: const AmendMovementRequest(correction: expense),
      );

      expect(
        adapter.request.uri.path,
        '/api/movements/0a3c0f5e-2b1d-4c8e-9f00-000000000009/amendments',
      );
      expect(adapter.sentJson, <String, Object?>{
        'correction': expense.toJson(),
      });
    },
  );

  test('una fecha de calendario viaja en el query como fecha', () async {
    adapter.answer(200, _summary);

    await api.book.bookSummary(asOf: '2026-11-02');

    expect(adapter.request.uri.queryParameters, <String, String>{
      'asOf': '2026-11-02',
    });
  });

  test(
    'la dirección la pone quien construye el cliente; sin ella no hay host',
    () async {
      final _RecordingAdapter bare = _RecordingAdapter()
        ..answer(200, _movement);
      final FinanceApi withoutAddress = FinanceApi(
        Dio()..httpClientAdapter = bare,
      );

      await withoutAddress.movements.movementsRevisions(id: 'x');

      expect(bare.request.uri.host, isEmpty);
    },
  );
}

const Map<String, Object?> _movement = <String, Object?>{
  'id': '0a3c0f5e-2b1d-4c8e-9f00-000000000001',
  'date': '2026-11-02',
  'description': 'Almuerzo',
  'source': 'APP',
  'entries': <Object?>[],
};

const Map<String, Object?> _summary = <String, Object?>{
  'asOf': '2026-11-02',
  'monthFrom': '2026-11-01',
  'moneyReallyMineInCents': 0,
  'incomeThatMonthInCents': 0,
  'expensesThatMonthInCents': 1234,
  'totalsByTypeInCents': <String, Object?>{},
};

/// Un transporte que anota lo que se le pide y contesta lo que se le diga.
class _RecordingAdapter implements HttpClientAdapter {
  late RequestOptions request;
  Object? sentJson;
  int _status = 200;
  Object? _body;

  void answer(int status, Object? body) {
    _status = status;
    _body = body;
  }

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    request = options;
    if (requestStream != null) {
      final List<int> bytes = <int>[
        for (final Uint8List chunk in await requestStream.toList()) ...chunk,
      ];
      sentJson = jsonDecode(utf8.decode(bytes));
    } else {
      sentJson = null;
    }
    return ResponseBody.fromString(
      jsonEncode(_body),
      _status,
      headers: <String, List<String>>{
        Headers.contentTypeHeader: <String>[Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
