// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:dio/dio.dart';
import 'package:retrofit/retrofit.dart';

import '../models/balances_response.dart';
import '../models/summary_response.dart';

part 'book_client.g.dart';

@RestApi()
abstract class BookClient {
  factory BookClient(Dio dio, {String? baseUrl}) = _BookClient;

  @GET('/api/balances')
  Future<BalancesResponse> bookBalances({@Query('asOf') String? asOf});

  @GET('/api/summary')
  Future<SummaryResponse> bookSummary({@Query('asOf') String? asOf});
}
