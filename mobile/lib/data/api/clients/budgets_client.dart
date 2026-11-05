// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:dio/dio.dart';
import 'package:retrofit/retrofit.dart';

import '../models/budget_response.dart';
import '../models/budgets_response.dart';
import '../models/set_budget_request.dart';

part 'budgets_client.g.dart';

@RestApi()
abstract class BudgetsClient {
  factory BudgetsClient(Dio dio, {String? baseUrl}) = _BudgetsClient;

  @GET('/api/budgets')
  Future<BudgetsResponse> budgetsBudgets({@Query('month') String? month});

  @POST('/api/budgets')
  Future<BudgetResponse> budgetsSet({@Body() required SetBudgetRequest body});
}
