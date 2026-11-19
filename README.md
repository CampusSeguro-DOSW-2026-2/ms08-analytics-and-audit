# MS08 – Auditoría y analítica (CampusSeguro)

Registra de forma inmutable los hechos que envían los microservicios, permite consultarlos y calcula indicadores por evento (simulacro o real).
Puerto **8088**. Java 21, Spring Boot 4.1, MongoDB.

> Estado: **paso 4 (consulta)**. Existen `POST` (ingesta) y `GET` (consulta) de `/api/v1/audit/events`; los indicadores vienen en los pasos 6 y 7.
> Contrato y decisiones: `ms08-analytics-and-audit/` en el repo `documentation` (ADR-001, `api/swagger.yml`).

## Requisitos
- JDK 21, Maven 3.9+
- Docker (MongoDB local y pruebas de integración con Testcontainers)

## Ejecutar en local
```bash
cd infra && docker compose up -d      # MongoDB en localhost:27018
cd .. && MONGODB_URI=mongodb://localhost:27018/campusseguro_audit mvn spring-boot:run
curl http://localhost:8088/actuator/health   # {"status":"UP"}
```

## Variables de entorno
| Variable | Por defecto | Uso |
|----------|-------------|-----|
| `MONGODB_URI` | `mongodb://localhost:27017/campusseguro_audit` | Conexión a MongoDB. En producción es un secreto: solo por entorno, nunca en el repo. |
| `SERVER_PORT` | `8088` | Puerto |
| `AUDIT_RETENTION_DAYS` | `180` | Retención de la auditoría (6 meses) |
| `APP_TIMEZONE` | `America/Bogota` | Zona horaria |
| `AUDIT_TTL_INDEX_ENABLED` | `true` | Crea el índice TTL sobre `fecha_hora` (borrado automático a los 180 días) |
| `AUDIT_CLEANUP_JOB_ENABLED` | `false` | Respaldo si la base no soporta TTL: job diario que borra lo vencido (poner también el TTL en `false`) |

## Seguridad
MS08 va detrás del API Gateway: no valida JWT ni hace CORS. Confía en las cabeceras que inyecta el GW:
- Usuario: `X-User-Id` (UUID) + `X-User-Permission` (códigos separados por coma). Cada código es una autoridad.
- Servicio (ruta Interna): `X-Service-Name` = `ms01`…`ms08`.
- Sin cabeceras → 401. Cabeceras inválidas → 401. Sin permiso → 403.
- Errores en formato 7.1 `{status, codigo, mensaje, errores, correlation_id}`.

## Ingesta
`POST /api/v1/audit/events` (ruta Interna: solo con `X-Service-Name` de otro microservicio).
- 202 sin cuerpo; también 202 si el mismo `id` ya existe (reintentos seguros).
- Sin `id`, el identificador es un SHA-256 de `correlation_id + modulo + accion + recurso_afectado + fecha_hora`.
- 400 `VALIDACION` con el campo en `errores`: campos desconocidos, `fecha_hora` a más de 5 min en el futuro o más vieja que la retención, `tipo_evento` obligatorio con `evento_id`, `SIMULACRO` sin `evento_id`, `atributos` (≤ 20 claves snake_case; texto ≤ 100, número o booleano).
- 401 sin identidad; 403 si lo llama un usuario; 503 si la base no responde.
- La colección es solo de inserción: se usa `insert`, nunca `save`.

## Consulta
`GET /api/v1/audit/events` y `GET /api/v1/audit/events/{id}` (ruta Protegida, usuarios con permiso; un servicio recibe 403).

| Permiso | Registros que ve |
|---------|------------------|
| `ms08:audit_drill:read` | `tipo_evento = SIMULACRO` |
| `ms08:audit_real_event:read` | `tipo_evento = REAL` (con o sin `evento_id`) |
| Ambos | Todo, incluidos los administrativos (sin evento ni tipo, por ejemplo un login) |

- Sin ninguno de los dos: 403. Pedir un `tipo_evento` que el usuario no puede leer: 403.
- Filtros opcionales: `modulo`, `accion`, `usuario_id`, `evento_id`, `tipo_evento`, `recurso_afectado`, `correlation_id`, `desde`, `hasta` (ISO-8601, ambos incluidos), `page` (desde 0) y `size` (1 a 100, por defecto 20).
- Orden: más recientes primero. Respuesta: `contenido`, `page`, `size`, `total_elementos`, `total_paginas`.
- Detalle por `id`: 404 si no existe; 403 si su tipo no es legible por el usuario.

## Indicadores por evento
`GET /api/v1/analytics/events/{evento_id}` (ruta Protegida). Se calculan al consultar con los hechos del evento, así que incluyen los hechos que lleguen tarde. No hay colección de indicadores.

| Permiso | Eventos que puede consultar |
|---------|-----------------------------|
| `ms08:indicators_drill:read` | Evento de tipo `SIMULACRO` |
| `ms08:indicators_real_event:read` | Evento de tipo `REAL` |

- 400 si el id no es UUID; 404 si MS08 no tiene hechos de ese evento; 403 si el permiso no corresponde al tipo del evento (los permisos de auditoría no sirven aquí).
- Porcentajes de 0 a 100 con un decimal (`null` si no hay con qué dividir); tiempos en segundos (`null` si no hay muestras).
- Solo cuentan los hechos con `resultado` `EXITO`; en las notificaciones (`NOTIFICATION_SENT`/`NOTIFICATION_FAILED`) cuentan ambos resultados.
- Fuentes: MS03 (`NOTIFICATION_*`, `CONFIRM`), MS04 (`REGISTRO_CHECKIN`, `CAMBIO_ESTADO_CHECKIN`), MS05 (`CREAR_INCIDENTE`, `INICIAR_ATENCION`) y MS06 (`EVENT_STARTED`). Se identifica cada hecho por `modulo` + `accion`.
- Sin `EVENT_STARTED` no hay tiempo hasta el check-in ni de evacuación (`null`); lo demás se calcula igual. Un check-in anterior al inicio no cuenta como tiempo de llegada.
- Con varias alertas en el mismo evento los destinatarios se suman: si una persona está en dos alertas se cuenta dos veces.

## Comparación y ubicaciones
Ambas reciben los eventos como `evento_id` repetido (`?evento_id=a&evento_id=b`) o separado por comas, y usan los mismos permisos de indicadores que el endpoint por evento. Todos los eventos deben ser del mismo tipo (si no, 400).

- `GET /api/v1/analytics/comparisons` compara de 2 a 10 eventos: una fila por evento, del inicio más antiguo al más reciente (los que no iniciaron, al final), con participación, confirmación, asistencia, incidentes, tiempos y `cambio_vs_anterior` (actual menos anterior; `null` si falta alguno de los dos valores).
- `GET /api/v1/analytics/locations` suma de 1 a 10 eventos por edificio o zona (`ubicacion_tipo` + `ubicacion_id`): incidentes, solicitudes de asistencia (usuarios distintos en `NEEDS_ASSISTANCE`), tiempo medio hasta la primera atención y el detalle `por_evento`. Orden: el más lento primero; los lugares sin muestras, al final.
- Los hechos sin `ubicacion_tipo` y `ubicacion_id` no se asignan a ningún lugar.
- Ids repetidos (también en mayúsculas) cuentan una vez. 400 si quedan menos de 2 (1 en ubicaciones) o más de 10; 404 si un evento no tiene hechos; 403 si el permiso no corresponde al tipo.

## Estructura
Igual que MS03/MS06: `config`, `security`, `exception` y, en los pasos siguientes, `controller`, `dto`, `entity`, `mapper`, `repository`, `service`. El `X-Correlation-ID` (`CorrelationIds`, `CorrelationIdFilter`) y `ErrorResponseWriter` están en `security`, como en MS03.

## Pruebas
```bash
mvn clean verify   # unitarias + integración (Docker) + cobertura JaCoCo >= 80 %
```
