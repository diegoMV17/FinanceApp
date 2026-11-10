import '../errors/request_failure.dart';

/// Una lectura del servidor, ya traducida al modelo de la app, con el
/// momento en que se leyó.
///
/// Si [staleBecause] no es nulo, es la última copia guardada y no lo de ahora:
/// la pantalla tiene que decir de cuándo es y por qué no hay algo más nuevo.
class Reading<T> {
  const Reading(this.value, this.readAt, [this.staleBecause]);

  final T value;

  /// Un instante, en UTC.
  final DateTime readAt;

  final RequestFailure? staleBecause;

  bool get isStale => staleBecause != null;
}
