import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/dates/calendar_date.dart';
import '../../../core/dates/clock.dart';
import '../../../core/forms/submit_state.dart';
import '../../../core/ids/uuid.dart';
import '../../../core/ledger/account_ref.dart';
import '../../../core/ledger/movement_draft.dart';
import '../../../core/money/amount_input.dart';
import '../../../core/money/money.dart';
import '../../../core/reading/reading.dart';
import '../../../core/text/text_input.dart';
import '../data/movements_repository.dart';

/// Lo que se puede registrar desde la app.
enum MovementKind {
  expense('Gasto', 'Sale de', 'Categoría'),
  income('Ingreso', 'Entra a', 'Categoría'),
  transfer('Transferencia', 'De', 'A'),
  openingBalance('Saldo inicial', 'Cuenta', 'Patrimonio');

  const MovementKind(this.label, this.firstLabel, this.secondLabel);

  final String label;

  /// Cómo se llama cada hueco en la pantalla.
  final String firstLabel;
  final String secondLabel;

  bool get hasDescription => this != openingBalance;

  /// Qué cuentas se ofrecen en el primer hueco. Solo filtra lo que se
  /// muestra: si es válido lo decide el servidor.
  List<AccountRef> firstOptions(List<AccountRef> accounts) =>
      _of(accounts, AccountKind.asset);

  List<AccountRef> secondOptions(List<AccountRef> accounts) =>
      _of(accounts, switch (this) {
        expense => AccountKind.expense,
        income => AccountKind.income,
        transfer => AccountKind.asset,
        openingBalance => AccountKind.equity,
      });

  static List<AccountRef> _of(List<AccountRef> accounts, AccountKind kind) =>
      accounts.where((AccountRef a) => a.kind == kind).toList();
}

class RecordMovementState {
  const RecordMovementState({
    required this.kind,
    required this.date,
    this.first,
    this.second,
    this.submit = const SubmitState.editing(),
  });

  final MovementKind kind;
  final CalendarDate date;

  /// El id de la cuenta elegida en cada hueco.
  final String? first;
  final String? second;
  final SubmitState submit;

  RecordMovementState copyWith({
    MovementKind? kind,
    CalendarDate? date,
    String? Function()? first,
    String? Function()? second,
    SubmitState? submit,
  }) => RecordMovementState(
    kind: kind ?? this.kind,
    date: date ?? this.date,
    first: first == null ? this.first : first(),
    second: second == null ? this.second : second(),
    submit: submit ?? this.submit,
  );
}

/// Registrar un gasto, un ingreso, una transferencia o un saldo inicial.
///
/// El id se genera una vez por formulario: un doble toque, o volver a
/// intentar después de un fallo, deja un solo movimiento en la cola. Registrar
/// es guardarlo en la cola del teléfono; la cola lo envía, y el tablero lo
/// cuenta cuando el servidor lo aceptó.
class RecordMovementViewModel extends Notifier<RecordMovementState> {
  final String _id = generateUuidV4();

  @override
  RecordMovementState build() => RecordMovementState(
    kind: MovementKind.expense,
    date: CalendarDate.ofLocal(ref.read(clockProvider)()),
  );

  /// Cambiar de tipo deja los huecos vacíos: lo elegido para otro tipo no
  /// tiene por qué servir en este.
  void chooseKind(MovementKind kind) => state = state.copyWith(
    kind: kind,
    first: () => null,
    second: () => null,
    submit: const SubmitState.editing(),
  );

  void chooseFirst(String? accountId) =>
      state = state.copyWith(first: () => accountId);

  void chooseSecond(String? accountId) =>
      state = state.copyWith(second: () => accountId);

  void chooseDate(CalendarDate date) => state = state.copyWith(date: date);

  /// «Hoy» (0) o «Ayer» (1), según el reloj del teléfono en su zona. El día
  /// se resta en el calendario, no en horas: cruza bien el cambio de mes.
  void chooseDaysAgo(int days) {
    final DateTime now = ref.read(clockProvider)();
    chooseDate(
      CalendarDate.ofLocal(DateTime(now.year, now.month, now.day - days)),
    );
  }

  Future<void> submit({
    required String amount,
    required String description,
  }) async {
    if (state.submit.isSubmitting || state.submit.isDone) {
      return;
    }
    final MovementDraft? draft = _draft(amount, description);
    if (draft == null) {
      return;
    }
    _setSubmit(const SubmitState.submitting());
    SubmitState result;
    try {
      await (await ref.read(movementsRepositoryProvider.future)).record(draft);
      result = const SubmitState.done();
    } on MovementNotRecorded {
      result = const SubmitState.editing(
        'No se pudo guardar en el teléfono: este movimiento no quedó '
        'registrado. Intenta de nuevo.',
      );
    } on Object {
      result = const SubmitState.editing(
        'No se pudo registrar. Intenta de nuevo.',
      );
    }
    if (ref.mounted) {
      _setSubmit(result);
    }
  }

  MovementDraft? _draft(String amountText, String descriptionText) {
    final Money amount;
    try {
      amount = parseAmount(amountText);
    } on AmountInputException catch (invalid) {
      _setSubmit(SubmitState.editing(amountMessage(invalid.problem)));
      return null;
    }
    final String? first = state.first;
    final String? second = state.second;
    final MovementKind kind = state.kind;
    if (first == null) {
      _setSubmit(SubmitState.editing('Elige «${kind.firstLabel}».'));
      return null;
    }
    if (second == null) {
      _setSubmit(SubmitState.editing('Elige «${kind.secondLabel}».'));
      return null;
    }
    try {
      return switch (kind) {
        MovementKind.expense => ExpenseDraft(
          id: _id,
          date: state.date,
          amount: amount,
          description: descriptionText,
          paidFrom: first,
          category: second,
        ),
        MovementKind.income => IncomeDraft(
          id: _id,
          date: state.date,
          amount: amount,
          description: descriptionText,
          receivedInto: first,
          category: second,
        ),
        MovementKind.transfer => TransferDraft(
          id: _id,
          date: state.date,
          amount: amount,
          description: descriptionText,
          from: first,
          to: second,
        ),
        MovementKind.openingBalance => OpeningBalanceDraft(
          id: _id,
          date: state.date,
          amount: amount,
          account: first,
          equity: second,
        ),
      };
    } on TextInputException catch (invalid) {
      _setSubmit(
        SubmitState.editing(
          textMessage(invalid.problem, max: maxDescriptionLength),
        ),
      );
      return null;
    }
  }

  void _setSubmit(SubmitState submit) => state = state.copyWith(submit: submit);
}

final NotifierProvider<RecordMovementViewModel, RecordMovementState>
recordMovementViewModelProvider =
    NotifierProvider.autoDispose<RecordMovementViewModel, RecordMovementState>(
      RecordMovementViewModel.new,
    );

/// Las cuentas abiertas, para los huecos del formulario.
final FutureProvider<Reading<List<AccountRef>>> recordableAccountsProvider =
    FutureProvider.autoDispose<Reading<List<AccountRef>>>(
      (Ref ref) async =>
          (await ref.watch(movementsRepositoryProvider.future)).openAccounts(),
    );
