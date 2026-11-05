// GENERATED CODE - DO NOT MODIFY BY HAND

// ignore_for_file: unused_element

part of 'local_database.dart';

// ignore_for_file: type=lint
class $PendingMovementsTable extends PendingMovements
    with TableInfo<$PendingMovementsTable, PendingMovementRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $PendingMovementsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _positionMeta = const VerificationMeta(
    'position',
  );
  @override
  late final GeneratedColumn<int> position = GeneratedColumn<int>(
    'position',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways('UNIQUE'),
  );
  static const VerificationMeta _bodyMeta = const VerificationMeta('body');
  @override
  late final GeneratedColumn<String> body = GeneratedColumn<String>(
    'body',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  late final GeneratedColumnWithTypeConverter<PendingStatus, String> status =
      GeneratedColumn<String>(
        'status',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      ).withConverter<PendingStatus>($PendingMovementsTable.$converterstatus);
  static const VerificationMeta _problemTypeMeta = const VerificationMeta(
    'problemType',
  );
  @override
  late final GeneratedColumn<String> problemType = GeneratedColumn<String>(
    'problem_type',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    position,
    id,
    body,
    status,
    problemType,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'pending_movements';
  @override
  VerificationContext validateIntegrity(
    Insertable<PendingMovementRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('position')) {
      context.handle(
        _positionMeta,
        position.isAcceptableOrUnknown(data['position']!, _positionMeta),
      );
    }
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('body')) {
      context.handle(
        _bodyMeta,
        body.isAcceptableOrUnknown(data['body']!, _bodyMeta),
      );
    } else if (isInserting) {
      context.missing(_bodyMeta);
    }
    if (data.containsKey('problem_type')) {
      context.handle(
        _problemTypeMeta,
        problemType.isAcceptableOrUnknown(
          data['problem_type']!,
          _problemTypeMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {position};
  @override
  PendingMovementRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return PendingMovementRow(
      position: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}position'],
      )!,
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      body: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}body'],
      )!,
      status: $PendingMovementsTable.$converterstatus.fromSql(
        attachedDatabase.typeMapping.read(
          DriftSqlType.string,
          data['${effectivePrefix}status'],
        )!,
      ),
      problemType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}problem_type'],
      ),
    );
  }

  @override
  $PendingMovementsTable createAlias(String alias) {
    return $PendingMovementsTable(attachedDatabase, alias);
  }

  static JsonTypeConverter2<PendingStatus, String, String> $converterstatus =
      const EnumNameConverter<PendingStatus>(PendingStatus.values);
}

class PendingMovementRow extends DataClass
    implements Insertable<PendingMovementRow> {
  final int position;
  final String id;

  /// El cuerpo tal como viajará, en JSON.
  final String body;
  final PendingStatus status;

  /// El `type` del error del servidor. Nunca su texto ni el cuerpo entero.
  final String? problemType;
  const PendingMovementRow({
    required this.position,
    required this.id,
    required this.body,
    required this.status,
    this.problemType,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['position'] = Variable<int>(position);
    map['id'] = Variable<String>(id);
    map['body'] = Variable<String>(body);
    {
      map['status'] = Variable<String>(
        $PendingMovementsTable.$converterstatus.toSql(status),
      );
    }
    if (!nullToAbsent || problemType != null) {
      map['problem_type'] = Variable<String>(problemType);
    }
    return map;
  }

  PendingMovementsCompanion toCompanion(bool nullToAbsent) {
    return PendingMovementsCompanion(
      position: Value(position),
      id: Value(id),
      body: Value(body),
      status: Value(status),
      problemType: problemType == null && nullToAbsent
          ? const Value.absent()
          : Value(problemType),
    );
  }

  factory PendingMovementRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return PendingMovementRow(
      position: serializer.fromJson<int>(json['position']),
      id: serializer.fromJson<String>(json['id']),
      body: serializer.fromJson<String>(json['body']),
      status: $PendingMovementsTable.$converterstatus.fromJson(
        serializer.fromJson<String>(json['status']),
      ),
      problemType: serializer.fromJson<String?>(json['problemType']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'position': serializer.toJson<int>(position),
      'id': serializer.toJson<String>(id),
      'body': serializer.toJson<String>(body),
      'status': serializer.toJson<String>(
        $PendingMovementsTable.$converterstatus.toJson(status),
      ),
      'problemType': serializer.toJson<String?>(problemType),
    };
  }

  PendingMovementRow copyWith({
    int? position,
    String? id,
    String? body,
    PendingStatus? status,
    Value<String?> problemType = const Value.absent(),
  }) => PendingMovementRow(
    position: position ?? this.position,
    id: id ?? this.id,
    body: body ?? this.body,
    status: status ?? this.status,
    problemType: problemType.present ? problemType.value : this.problemType,
  );
  PendingMovementRow copyWithCompanion(PendingMovementsCompanion data) {
    return PendingMovementRow(
      position: data.position.present ? data.position.value : this.position,
      id: data.id.present ? data.id.value : this.id,
      body: data.body.present ? data.body.value : this.body,
      status: data.status.present ? data.status.value : this.status,
      problemType: data.problemType.present
          ? data.problemType.value
          : this.problemType,
    );
  }

  @override
  String toString() {
    return (StringBuffer('PendingMovementRow(')
          ..write('position: $position, ')
          ..write('id: $id, ')
          ..write('body: $body, ')
          ..write('status: $status, ')
          ..write('problemType: $problemType')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(position, id, body, status, problemType);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is PendingMovementRow &&
          other.position == this.position &&
          other.id == this.id &&
          other.body == this.body &&
          other.status == this.status &&
          other.problemType == this.problemType);
}

class PendingMovementsCompanion extends UpdateCompanion<PendingMovementRow> {
  final Value<int> position;
  final Value<String> id;
  final Value<String> body;
  final Value<PendingStatus> status;
  final Value<String?> problemType;
  const PendingMovementsCompanion({
    this.position = const Value.absent(),
    this.id = const Value.absent(),
    this.body = const Value.absent(),
    this.status = const Value.absent(),
    this.problemType = const Value.absent(),
  });
  PendingMovementsCompanion.insert({
    this.position = const Value.absent(),
    required String id,
    required String body,
    required PendingStatus status,
    this.problemType = const Value.absent(),
  }) : id = Value(id),
       body = Value(body),
       status = Value(status);
  static Insertable<PendingMovementRow> custom({
    Expression<int>? position,
    Expression<String>? id,
    Expression<String>? body,
    Expression<String>? status,
    Expression<String>? problemType,
  }) {
    return RawValuesInsertable({
      if (position != null) 'position': position,
      if (id != null) 'id': id,
      if (body != null) 'body': body,
      if (status != null) 'status': status,
      if (problemType != null) 'problem_type': problemType,
    });
  }

  PendingMovementsCompanion copyWith({
    Value<int>? position,
    Value<String>? id,
    Value<String>? body,
    Value<PendingStatus>? status,
    Value<String?>? problemType,
  }) {
    return PendingMovementsCompanion(
      position: position ?? this.position,
      id: id ?? this.id,
      body: body ?? this.body,
      status: status ?? this.status,
      problemType: problemType ?? this.problemType,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (position.present) {
      map['position'] = Variable<int>(position.value);
    }
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (body.present) {
      map['body'] = Variable<String>(body.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(
        $PendingMovementsTable.$converterstatus.toSql(status.value),
      );
    }
    if (problemType.present) {
      map['problem_type'] = Variable<String>(problemType.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('PendingMovementsCompanion(')
          ..write('position: $position, ')
          ..write('id: $id, ')
          ..write('body: $body, ')
          ..write('status: $status, ')
          ..write('problemType: $problemType')
          ..write(')'))
        .toString();
  }
}

abstract class _$LocalDatabase extends GeneratedDatabase {
  _$LocalDatabase(QueryExecutor e) : super(e);
  late final $PendingMovementsTable pendingMovements = $PendingMovementsTable(
    this,
  );
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [pendingMovements];
}
