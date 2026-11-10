import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/errors/request_failure.dart';
import '../../core/network/server_gateway.dart';
import '../../core/reading/reading.dart';
import '../api/finance_api.dart';
import '../local/read_snapshots_store.dart';

/// Leer del servidor y guardar la copia; sin servidor, la copia.
///
/// Es la regla de `mobile/CLAUDE.md`: lo leído se guarda como la última copia,
/// con su hora, para mostrar algo sin conexión. La copia es la respuesta tal
/// como llegó (vuelta a escribir desde el modelo generado) y nunca se edita.
/// Lo que no se puede traducir al modelo de la app no se guarda ni se
/// muestra: una respuesta con otra forma no tapa la última buena.
class ReadThrough {
  ReadThrough(this._gateway, this._snapshots, [this._now = DateTime.now]);

  final ServerGateway _gateway;
  final ReadSnapshotsStore _snapshots;
  final DateTime Function() _now;

  /// [G] es el modelo generado, [T] el de la app.
  Future<Reading<T>> read<G, T>({
    required String key,
    required Future<G> Function(FinanceApi api) fetch,
    required Map<String, Object?> Function(G response) toJson,
    required G Function(Map<String, Object?> json) fromJson,
    required T Function(G response) translate,
  }) async {
    final RequestFailure failure;
    try {
      final G response = await _gateway.call(fetch);
      final T value = _translated(response, translate);
      final DateTime readAt = _now().toUtc();
      try {
        await _snapshots.save(key, jsonEncode(toJson(response)), readAt);
      } on SnapshotNotSaved {
        // Lo leído se muestra igual: la copia es para después, sin conexión.
      }
      return Reading<T>(value, readAt);
    } on RequestFailure catch (failed) {
      failure = failed;
    }
    final ReadSnapshot? copy = await _snapshots.read(key);
    if (copy == null) {
      throw failure;
    }
    try {
      final Object? json = jsonDecode(copy.body);
      final G response = fromJson(json! as Map<String, Object?>);
      return Reading<T>(translate(response), copy.readAt, failure);
    } on Object {
      // Una copia que ya no se entiende (otra versión de la app) no se
      // muestra a medias.
      throw failure;
    }
  }

  static T _translated<G, T>(G response, T Function(G) translate) {
    try {
      return translate(response);
    } on Object {
      throw const RequestFailure(FailureKind.unexpected);
    }
  }
}

final FutureProvider<ReadThrough> readThroughProvider =
    FutureProvider<ReadThrough>(
      (Ref ref) async => ReadThrough(
        await ref.watch(serverGatewayProvider.future),
        await ref.watch(readSnapshotsStoreProvider.future),
      ),
    );
