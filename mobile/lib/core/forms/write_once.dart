import '../errors/request_failure.dart';
import 'submit_state.dart';

/// Hace la escritura y dice cómo terminó el formulario: listo, o de vuelta a
/// editar con el porqué que se le muestra al dueño. Nunca deja salir el
/// texto de una excepción: un error de Dio trae la petición entera.
Future<SubmitState> writeOnce(Future<void> Function() write) async {
  try {
    await write();
    return const SubmitState.done();
  } on RequestFailure catch (failure) {
    return SubmitState.editing(failure.message);
  } on Object {
    return const SubmitState.editing('No se pudo completar. Intenta de nuevo.');
  }
}
