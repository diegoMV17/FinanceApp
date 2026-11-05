import 'package:drift/drift.dart';

/// En qué está un movimiento de la cola.
enum PendingStatus {
  /// Por enviar, o por reintentar.
  waiting,

  /// El servidor dijo 409: lo tiene que resolver el usuario.
  conflict,

  /// El servidor lo rechazó con otro 4xx: no se reintenta.
  rejected,
}

/// La cola de movimientos pendientes. `position` da el orden de llegada.
@DataClassName('PendingMovementRow')
class PendingMovements extends Table {
  IntColumn get position => integer().autoIncrement()();

  TextColumn get id => text().unique()();

  /// El cuerpo tal como viajará, en JSON.
  TextColumn get body => text()();

  TextColumn get status => textEnum<PendingStatus>()();

  /// El `type` del error del servidor. Nunca su texto ni el cuerpo entero.
  TextColumn get problemType => text().nullable()();
}

/// Lo encolado no cambia: reenviar otro contenido con el mismo id sería un 409.
/// En SQL y no solo en el almacén, para que siga en pie si mañana alguien
/// escribe otra consulta.
const String pendingMovementsKeepTheirContent = '''
CREATE TRIGGER pending_movements_keep_their_content
BEFORE UPDATE OF id, body, position ON pending_movements
BEGIN
  SELECT RAISE(ABORT, 'un movimiento encolado no cambia');
END;
''';
