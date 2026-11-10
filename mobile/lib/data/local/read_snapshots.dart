import 'package:drift/drift.dart';

/// La última copia de cada lectura del servidor, con el momento en que se
/// leyó: es lo que se muestra sin conexión, avisando que puede estar
/// desactualizada.
///
/// `body` es la respuesta tal como llegó, en JSON. Nunca se arma ni se
/// corrige en el teléfono: el servidor es la verdad, y una copia editada aquí
/// sería una segunda.
@DataClassName('ReadSnapshotRow')
class ReadSnapshots extends Table {
  /// Qué se leyó: `balances`, `budgets/2026-11`... La pone el repositorio.
  TextColumn get key => text()();

  TextColumn get body => text()();

  /// Cuándo se leyó (un instante; Drift lo guarda en segundos Unix).
  DateTimeColumn get readAt => dateTime()();

  @override
  Set<Column<Object>> get primaryKey => <Column<Object>>{key};
}

/// La copia solo se reemplaza por una lectura más nueva: una respuesta lenta
/// que llega después no tapa la que ya se guardó, y nada cambia el cuerpo sin
/// una lectura nueva ni mueve una copia a otra clave. En SQL y no solo en el
/// almacén, para que siga en pie si mañana alguien escribe otra consulta.
const String readSnapshotsOnlyGetNewer = '''
CREATE TRIGGER read_snapshots_only_get_newer
BEFORE UPDATE ON read_snapshots
WHEN NEW.key IS NOT OLD.key OR NEW.read_at <= OLD.read_at
BEGIN
  SELECT RAISE(ABORT, 'la última copia solo la reemplaza una lectura más nueva');
END;
''';
