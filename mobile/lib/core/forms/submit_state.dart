import '../money/amount_input.dart';
import '../text/text_input.dart';

/// En qué está un formulario que escribe algo.
enum SubmitPhase {
  /// Se puede escribir y enviar. Si hay [SubmitState.error], es por qué el
  /// último intento no salió.
  editing,

  /// Enviando: otro toque no hace nada.
  submitting,

  /// Listo: la pantalla se cierra.
  done,
}

class SubmitState {
  const SubmitState.editing([this.error]) : phase = SubmitPhase.editing;

  const SubmitState.submitting() : phase = SubmitPhase.submitting, error = null;

  const SubmitState.done() : phase = SubmitPhase.done, error = null;

  final SubmitPhase phase;

  /// Lo que se le dice al dueño; nunca el texto de una excepción.
  final String? error;

  bool get isSubmitting => phase == SubmitPhase.submitting;

  bool get isDone => phase == SubmitPhase.done;
}

/// Por qué un monto escrito no sirve, para el dueño.
String amountMessage(AmountProblem problem) => switch (problem) {
  AmountProblem.empty => 'Escribe el monto.',
  AmountProblem.malformed =>
    'Escribe el monto en pesos, con punto de miles y coma decimal: 12.345,67.',
  AmountProblem.notPositive => 'El monto tiene que ser mayor que cero.',
  AmountProblem.tooLarge => 'El monto es demasiado grande.',
};

/// Por qué un texto escrito no sirve, para el dueño.
String textMessage(TextProblem problem, {required int max}) =>
    switch (problem) {
      TextProblem.blank => 'Escribe un nombre.',
      TextProblem.tooLong => 'Es demasiado largo: máximo $max caracteres.',
    };
