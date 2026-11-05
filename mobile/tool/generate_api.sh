#!/usr/bin/env bash
# Regenera el cliente de la API (lib/data/api/) desde ../contract/openapi.json.
#
# Es lo único que se corre para eso, aquí y en CI: el trabajo `mobile` lo corre
# y falla si lo generado no es lo comiteado. lib/data/api/ no se edita a mano;
# si algo de lo generado está mal, se arregla aquí o en swagger_parser.yaml.
#
# Uso, desde cualquier sitio: mobile/tool/generate_api.sh
set -Eeuo pipefail
cd "$(dirname "$0")/.."

# 1. El contrato ajustado para el generador (las fechas de calendario como
#    texto; ver tool/contract_for_dart.dart). Va a build/, que no se versiona.
dart run tool/contract_for_dart.dart ../contract/openapi.json build/contract/openapi.json

# 2. Desde cero: un esquema que desaparece del contrato no deja su archivo.
rm -rf lib/data/api
dart run swagger_parser

# 3. Los .g.dart de json_serializable y retrofit, y el mismo formato que el
#    resto del código.
dart run build_runner build --delete-conflicting-outputs
dart format lib/data/api
