// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:dio/dio.dart';
import 'package:retrofit/retrofit.dart';

import '../models/account_response.dart';
import '../models/movements_response.dart';
import '../models/open_account_request.dart';
import '../models/rename_account_request.dart';

part 'accounts_client.g.dart';

@RestApi()
abstract class AccountsClient {
  factory AccountsClient(Dio dio, {String? baseUrl}) = _AccountsClient;

  @POST('/api/accounts')
  Future<AccountResponse> accountsOpen({
    @Body() required OpenAccountRequest body,
  });

  @POST('/api/accounts/{id}/archivals')
  Future<AccountResponse> accountsArchive({@Path('id') required String id});

  @GET('/api/accounts/{id}/movements')
  Future<MovementsResponse> accountsMovements({
    @Path('id') required String id,
    @Query('page') int? page,
    @Query('size') int? size,
  });

  @POST('/api/accounts/{id}/renames')
  Future<AccountResponse> accountsRename({
    @Path('id') required String id,
    @Body() required RenameAccountRequest body,
  });
}
