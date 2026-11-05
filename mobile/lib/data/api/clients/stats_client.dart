// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:dio/dio.dart';
import 'package:retrofit/retrofit.dart';

import '../models/account_type.dart';
import '../models/over_time_response.dart';
import '../models/totals_by_account_response.dart';

part 'stats_client.g.dart';

@RestApi()
abstract class StatsClient {
  factory StatsClient(Dio dio, {String? baseUrl}) = _StatsClient;

  @GET('/api/stats/by-account')
  Future<TotalsByAccountResponse> statsByAccount({
    @Query('from') String? from,
    @Query('to') String? to,
    @Query('type') AccountType? type,
    @Query('account') List<String>? account,
    @Query('person') String? person,
    @Query('minInCents') int? minInCents,
    @Query('maxInCents') int? maxInCents,
  });

  @GET('/api/stats/over-time')
  Future<OverTimeResponse> statsOverTime({
    @Query('from') String? from,
    @Query('to') String? to,
  });
}
