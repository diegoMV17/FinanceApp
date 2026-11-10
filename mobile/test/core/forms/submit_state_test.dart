import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/core/forms/submit_state.dart';
import 'package:mobile/core/money/amount_input.dart';
import 'package:mobile/core/text/text_input.dart';

void main() {
  test('cada problema de un monto tiene su mensaje, distinto', () {
    expect(
      AmountProblem.values.map(amountMessage).toSet(),
      hasLength(AmountProblem.values.length),
    );
  });

  test('el mensaje de un texto largo dice el tope', () {
    expect(textMessage(TextProblem.tooLong, max: 60), contains('60'));
    expect(textMessage(TextProblem.blank, max: 60), isNotEmpty);
  });

  test('las tres fases', () {
    expect(const SubmitState.editing().phase, SubmitPhase.editing);
    expect(const SubmitState.editing('x').error, 'x');
    expect(const SubmitState.submitting().isSubmitting, isTrue);
    expect(const SubmitState.done().isDone, isTrue);
  });
}
