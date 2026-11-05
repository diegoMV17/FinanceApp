// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:dio/dio.dart';

import 'clients/accounts_client.dart';
import 'clients/book_client.dart';
import 'clients/budgets_client.dart';
import 'clients/movements_client.dart';
import 'clients/people_client.dart';
import 'clients/stats_client.dart';

/// FinanceApp API `v1`.
///
/// El libro de finanzas personales, en partida doble. Toda ruta exige el token.
class FinanceApi {
  FinanceApi(Dio dio, {String? baseUrl}) : _dio = dio, _baseUrl = baseUrl;

  final Dio _dio;
  final String? _baseUrl;

  static String get version => '1';

  AccountsClient? _accounts;
  BookClient? _book;
  BudgetsClient? _budgets;
  MovementsClient? _movements;
  PeopleClient? _people;
  StatsClient? _stats;

  AccountsClient get accounts =>
      _accounts ??= AccountsClient(_dio, baseUrl: _baseUrl);

  BookClient get book => _book ??= BookClient(_dio, baseUrl: _baseUrl);

  BudgetsClient get budgets =>
      _budgets ??= BudgetsClient(_dio, baseUrl: _baseUrl);

  MovementsClient get movements =>
      _movements ??= MovementsClient(_dio, baseUrl: _baseUrl);

  PeopleClient get people => _people ??= PeopleClient(_dio, baseUrl: _baseUrl);

  StatsClient get stats => _stats ??= StatsClient(_dio, baseUrl: _baseUrl);
}
