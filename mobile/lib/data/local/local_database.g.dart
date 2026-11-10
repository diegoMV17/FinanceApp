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

class $ReadSnapshotsTable extends ReadSnapshots
    with TableInfo<$ReadSnapshotsTable, ReadSnapshotRow> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $ReadSnapshotsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _keyMeta = const VerificationMeta('key');
  @override
  late final GeneratedColumn<String> key = GeneratedColumn<String>(
    'key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
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
  static const VerificationMeta _readAtMeta = const VerificationMeta('readAt');
  @override
  late final GeneratedColumn<DateTime> readAt = GeneratedColumn<DateTime>(
    'read_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [key, body, readAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'read_snapshots';
  @override
  VerificationContext validateIntegrity(
    Insertable<ReadSnapshotRow> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('key')) {
      context.handle(
        _keyMeta,
        key.isAcceptableOrUnknown(data['key']!, _keyMeta),
      );
    } else if (isInserting) {
      context.missing(_keyMeta);
    }
    if (data.containsKey('body')) {
      context.handle(
        _bodyMeta,
        body.isAcceptableOrUnknown(data['body']!, _bodyMeta),
      );
    } else if (isInserting) {
      context.missing(_bodyMeta);
    }
    if (data.containsKey('read_at')) {
      context.handle(
        _readAtMeta,
        readAt.isAcceptableOrUnknown(data['read_at']!, _readAtMeta),
      );
    } else if (isInserting) {
      context.missing(_readAtMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {key};
  @override
  ReadSnapshotRow map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return ReadSnapshotRow(
      key: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}key'],
      )!,
      body: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}body'],
      )!,
      readAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}read_at'],
      )!,
    );
  }

  @override
  $ReadSnapshotsTable createAlias(String alias) {
    return $ReadSnapshotsTable(attachedDatabase, alias);
  }
}

class ReadSnapshotRow extends DataClass implements Insertable<ReadSnapshotRow> {
  /// Qué se leyó: `balances`, `budgets/2026-11`... La pone el repositorio.
  final String key;
  final String body;

  /// Cuándo se leyó (un instante; Drift lo guarda en segundos Unix).
  final DateTime readAt;
  const ReadSnapshotRow({
    required this.key,
    required this.body,
    required this.readAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['key'] = Variable<String>(key);
    map['body'] = Variable<String>(body);
    map['read_at'] = Variable<DateTime>(readAt);
    return map;
  }

  ReadSnapshotsCompanion toCompanion(bool nullToAbsent) {
    return ReadSnapshotsCompanion(
      key: Value(key),
      body: Value(body),
      readAt: Value(readAt),
    );
  }

  factory ReadSnapshotRow.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return ReadSnapshotRow(
      key: serializer.fromJson<String>(json['key']),
      body: serializer.fromJson<String>(json['body']),
      readAt: serializer.fromJson<DateTime>(json['readAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'key': serializer.toJson<String>(key),
      'body': serializer.toJson<String>(body),
      'readAt': serializer.toJson<DateTime>(readAt),
    };
  }

  ReadSnapshotRow copyWith({String? key, String? body, DateTime? readAt}) =>
      ReadSnapshotRow(
        key: key ?? this.key,
        body: body ?? this.body,
        readAt: readAt ?? this.readAt,
      );
  ReadSnapshotRow copyWithCompanion(ReadSnapshotsCompanion data) {
    return ReadSnapshotRow(
      key: data.key.present ? data.key.value : this.key,
      body: data.body.present ? data.body.value : this.body,
      readAt: data.readAt.present ? data.readAt.value : this.readAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('ReadSnapshotRow(')
          ..write('key: $key, ')
          ..write('body: $body, ')
          ..write('readAt: $readAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(key, body, readAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is ReadSnapshotRow &&
          other.key == this.key &&
          other.body == this.body &&
          other.readAt == this.readAt);
}

class ReadSnapshotsCompanion extends UpdateCompanion<ReadSnapshotRow> {
  final Value<String> key;
  final Value<String> body;
  final Value<DateTime> readAt;
  final Value<int> rowid;
  const ReadSnapshotsCompanion({
    this.key = const Value.absent(),
    this.body = const Value.absent(),
    this.readAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  ReadSnapshotsCompanion.insert({
    required String key,
    required String body,
    required DateTime readAt,
    this.rowid = const Value.absent(),
  }) : key = Value(key),
       body = Value(body),
       readAt = Value(readAt);
  static Insertable<ReadSnapshotRow> custom({
    Expression<String>? key,
    Expression<String>? body,
    Expression<DateTime>? readAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (key != null) 'key': key,
      if (body != null) 'body': body,
      if (readAt != null) 'read_at': readAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  ReadSnapshotsCompanion copyWith({
    Value<String>? key,
    Value<String>? body,
    Value<DateTime>? readAt,
    Value<int>? rowid,
  }) {
    return ReadSnapshotsCompanion(
      key: key ?? this.key,
      body: body ?? this.body,
      readAt: readAt ?? this.readAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (key.present) {
      map['key'] = Variable<String>(key.value);
    }
    if (body.present) {
      map['body'] = Variable<String>(body.value);
    }
    if (readAt.present) {
      map['read_at'] = Variable<DateTime>(readAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('ReadSnapshotsCompanion(')
          ..write('key: $key, ')
          ..write('body: $body, ')
          ..write('readAt: $readAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$LocalDatabase extends GeneratedDatabase {
  _$LocalDatabase(QueryExecutor e) : super(e);
  late final $PendingMovementsTable pendingMovements = $PendingMovementsTable(
    this,
  );
  late final $ReadSnapshotsTable readSnapshots = $ReadSnapshotsTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    pendingMovements,
    readSnapshots,
  ];
}
