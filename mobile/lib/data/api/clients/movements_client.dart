// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:dio/dio.dart';
import 'package:retrofit/retrofit.dart';

import '../models/account_type.dart';
import '../models/amend_movement_request.dart';
import '../models/cancel_movement_request.dart';
import '../models/cancellation_response.dart';
import '../models/history_response.dart';
import '../models/movement_request.dart';
import '../models/movement_response.dart';
import '../models/revisions_response.dart';

part 'movements_client.g.dart';

@RestApi()
abstract class MovementsClient {
  factory MovementsClient(Dio dio, {String? baseUrl}) = _MovementsClient;

  @GET('/api/movements')
  Future<HistoryResponse> movementsHistory({
    @Query('from') String? from,
    @Query('to') String? to,
    @Query('type') AccountType? type,
    @Query('account') List<String>? account,
    @Query('person') String? person,
    @Query('minInCents') int? minInCents,
    @Query('maxInCents') int? maxInCents,
    @Query('page') int? page,
    @Query('size') int? size,
  });

  @POST('/api/movements')
  Future<MovementResponse> movementsRecord({
    @Body() required MovementRequest body,
  });

  @POST('/api/movements/{id}/amendments')
  Future<MovementResponse> movementsAmend({
    @Path('id') required String id,
    @Body() required AmendMovementRequest body,
  });

  @POST('/api/movements/{id}/cancellations')
  Future<CancellationResponse> movementsCancel({
    @Path('id') required String id,
    @Body() required CancelMovementRequest body,
  });

  @GET('/api/movements/{id}/revisions')
  Future<RevisionsResponse> movementsRevisions({
    @Path('id') required String id,
  });
}
