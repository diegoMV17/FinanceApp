/// Lo que salió mal al hablar con el servidor, ya clasificado, y lo que se le
/// dice al dueño.
///
/// El texto del error que manda el servidor (`detail`) nunca se muestra: se
/// traduce su `type`, de la lista cerrada del contrato, a un mensaje escrito
/// aquí. Un test exige que cada `type` del contrato tenga el suyo.
library;

/// De qué clase fue el fallo.
enum FailureKind {
  /// La app está bloqueada: nada sale a la red.
  locked,

  /// No llegó: red caída, Tailscale apagado, tiempo de espera, certificado.
  noAnswer,

  /// 401: el token ya no es el del servidor.
  unauthorized,

  /// 429: demasiados intentos fallidos desde esta red.
  tooManyAttempts,

  /// 503: el libro está ocupado.
  busy,

  /// Un 4xx con un `Problem` de la API: el servidor no lo aceptó y dijo por
  /// qué.
  refused,

  /// Respondió algo que no es la API, o un error suyo.
  unexpected,
}

class RequestFailure implements Exception {
  const RequestFailure(this.kind) : problemType = null;

  const RequestFailure.refused(String this.problemType)
    : kind = FailureKind.refused;

  final FailureKind kind;

  /// El `type` del `Problem` (`urn:finance:error:...`), solo en [FailureKind.refused].
  final String? problemType;

  /// Lo que se le dice al dueño.
  String get message => switch (kind) {
    FailureKind.locked => 'La app está bloqueada.',
    FailureKind.noAnswer =>
      'No se pudo llegar al servidor. Revisa la conexión y que Tailscale '
          'esté encendido.',
    FailureKind.unauthorized =>
      'El servidor no reconoce el token. Si lo cambiaste, vuelve a '
          'configurar la conexión.',
    FailureKind.tooManyAttempts =>
      'Demasiados intentos fallidos desde esta red. Espera un momento.',
    FailureKind.busy => 'El servidor está ocupado. Intenta en un momento.',
    FailureKind.refused => _problemMessages[_code(problemType!)] ?? _unknown,
    FailureKind.unexpected =>
      'El servidor respondió algo inesperado. Intenta de nuevo.',
  };

  @override
  bool operator ==(Object other) =>
      other is RequestFailure &&
      other.kind == kind &&
      other.problemType == problemType;

  @override
  int get hashCode => Object.hash(kind, problemType);

  @override
  String toString() => problemType == null
      ? 'RequestFailure(${kind.name})'
      : 'RequestFailure(${kind.name}, $problemType)';
}

/// El prefijo de todo `type` de la API.
const String apiProblemPrefix = 'urn:finance:error:';

/// Si la app tiene un mensaje para este `type`.
bool knowsProblemType(String type) =>
    type.startsWith(apiProblemPrefix) &&
    _problemMessages.containsKey(_code(type));

/// Los `type` que la app sabe decir, completos.
Iterable<String> knownProblemTypes() =>
    _problemMessages.keys.map((String code) => '$apiProblemPrefix$code');

String _code(String type) => type.startsWith(apiProblemPrefix)
    ? type.substring(apiProblemPrefix.length)
    : '';

const String _unknown = 'El servidor no lo aceptó.';

/// La lista cerrada del servidor (`ApiError`), en español.
const Map<String, String> _problemMessages = <String, String>{
  'unauthorized': 'El servidor no reconoce el token.',
  'forbidden': 'El servidor no lo permite.',
  'too-many-attempts':
      'Demasiados intentos fallidos desde esta red. Espera un momento.',
  'malformed-request': 'El servidor no pudo leer lo que se envió.',
  'invalid-request': 'Falta un dato o tiene un valor que no se admite.',
  'invalid-path': 'La dirección pedida no es válida.',
  'rejected-request': 'El servidor rechazó lo que se envió.',
  'unsafe-text':
      'El texto tiene caracteres invisibles o de control. Escríbelo de nuevo.',
  'range-too-wide': 'El periodo pedido es demasiado largo.',
  'unsupported-media-type': 'El servidor no acepta ese formato.',
  'method-not-allowed': 'El servidor no admite esa operación.',
  'not-found': 'No existe en el servidor. Puede que se haya borrado.',
  'content-too-large': 'Lo que se envió es demasiado grande.',
  'duplicate-id':
      'Ya hay algo registrado con ese id y otro contenido. No se envió dos '
      'veces.',
  'duplicate-account-name': 'Ya hay una cuenta abierta con ese nombre.',
  'duplicate-person-name': 'Ya hay una persona con ese nombre.',
  'already-cancelled': 'Ese movimiento ya estaba anulado.',
  'already-archived': 'Esa cuenta ya estaba archivada.',
  'account-still-holds-money':
      'La cuenta todavía tiene saldo: no se puede archivar.',
  'person-already-archived': 'Esa persona ya estaba archivada.',
  'person-still-has-open-accounts':
      'La persona todavía tiene cuentas abiertas.',
  'budget-already-superseded':
      'Ese presupuesto ya fue reemplazado por otro más nuevo.',
  'unexpected-account-type': 'Esa cuenta no es del tipo que se necesita aquí.',
  'same-account-transfer':
      'Una transferencia no puede ir a la misma cuenta de la que sale.',
  'account-needs-name': 'La cuenta necesita un nombre.',
  'person-needs-name': 'La persona necesita un nombre.',
  'account-cannot-belong-to-a-person':
      'Solo lo que me deben, lo que debo o la plata de otro puede ser de una '
      'persona.',
  'archived-person': 'Esa persona está archivada.',
  'archived-account': 'Esa cuenta está archivada.',
  'date-out-of-range': 'La fecha está fuera del rango que admite el libro.',
  'reversed-range': 'Las fechas están al revés.',
  'rule-violated': 'El libro no lo admite.',
  'book-busy': 'El servidor está ocupado. Intenta en un momento.',
  'internal-error': 'El servidor tuvo un error. Intenta de nuevo.',
};
