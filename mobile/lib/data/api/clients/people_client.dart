// coverage:ignore-file
// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, unused_import, invalid_annotation_target, unnecessary_import

import 'package:dio/dio.dart';
import 'package:retrofit/retrofit.dart';

import '../models/account_response.dart';
import '../models/link_account_request.dart';
import '../models/open_person_request.dart';
import '../models/people_response.dart';
import '../models/person_detail_response.dart';
import '../models/person_response.dart';
import '../models/rename_person_request.dart';

part 'people_client.g.dart';

@RestApi()
abstract class PeopleClient {
  factory PeopleClient(Dio dio, {String? baseUrl}) = _PeopleClient;

  @GET('/api/people')
  Future<PeopleResponse> peoplePeople({@Query('asOf') String? asOf});

  @POST('/api/people')
  Future<PersonResponse> peopleOpen({@Body() required OpenPersonRequest body});

  @GET('/api/people/{id}')
  Future<PersonDetailResponse> peoplePerson({
    @Path('id') required String id,
    @Query('asOf') String? asOf,
  });

  @POST('/api/people/{id}/accounts')
  Future<AccountResponse> peopleLink({
    @Path('id') required String id,
    @Body() required LinkAccountRequest body,
  });

  @POST('/api/people/{id}/archivals')
  Future<PersonResponse> peopleArchive({@Path('id') required String id});

  @POST('/api/people/{id}/renames')
  Future<PersonResponse> peopleRename({
    @Path('id') required String id,
    @Body() required RenamePersonRequest body,
  });
}
