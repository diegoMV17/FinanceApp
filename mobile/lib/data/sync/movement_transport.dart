import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/network/api_client.dart';
import '../../core/storage/server_settings.dart';
import '../api/export.dart';

/// Lleva un movimiento al servidor. Solo habla: lo que significa la respuesta
/// se decide en `send_verdict.dart`.
class MovementTransport {
  const MovementTransport([this._dioFor = dioFor]);

  final Dio Function(ServerSettings) _dioFor;

  Future<MovementResponse> send(
    ServerSettings settings,
    MovementRequest movement,
  ) async {
    final Dio dio = _dioFor(settings);
    try {
      return await financeApiFor(dio).movements.movementsRecord(body: movement);
    } finally {
      dio.close();
    }
  }
}

final Provider<MovementTransport> movementTransportProvider =
    Provider<MovementTransport>((Ref ref) => const MovementTransport());
