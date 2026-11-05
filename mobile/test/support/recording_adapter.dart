import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

/// Un transporte de Dio que anota cada petición y contesta lo que se le diga:
/// sin red y sin servidor.
class RecordingAdapter implements HttpClientAdapter {
  final List<RequestOptions> requests = <RequestOptions>[];
  int _status = 200;
  Object? _body = const <String, Object?>{};
  Map<String, List<String>> _headers = const <String, List<String>>{};
  Object? _failure;

  RequestOptions get last => requests.last;

  void answer(
    int status, [
    Object? body = const <String, Object?>{},
    Map<String, List<String>> headers = const <String, List<String>>{},
  ]) {
    _status = status;
    _body = body;
    _headers = headers;
    _failure = null;
  }

  /// La próxima petición no llega: lanza esto, como lo haría la red.
  void fail(Object failure) => _failure = failure;

  /// Contestar según la petición, por encima de lo demás. Puede quedarse
  /// esperando, para ver qué pasa mientras una petición está en curso.
  Future<ResponseBody> Function(RequestOptions)? handler;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final Future<ResponseBody> Function(RequestOptions)? custom = handler;
    if (custom != null) {
      return custom(options);
    }
    final Object? failure = _failure;
    if (failure != null) {
      throw failure;
    }
    return ResponseBody.fromString(
      jsonEncode(_body),
      _status,
      headers: <String, List<String>>{
        Headers.contentTypeHeader: <String>[Headers.jsonContentType],
        ..._headers,
      },
    );
  }

  @override
  void close({bool force = false}) {}
}
