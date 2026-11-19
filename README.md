# MS08 – Auditoría y analítica (CampusSeguro)

Registra de forma inmutable los hechos que envían los microservicios, permite consultarlos y calcula indicadores por evento (simulacro o real).
Puerto **8088**. Java 21, Spring Boot 4.1, MongoDB.

> Estado: **paso 2 (proyecto base)**. Aún no hay endpoints de negocio (ingesta: paso 3; consulta: paso 4).
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

## Seguridad
MS08 va detrás del API Gateway: no valida JWT ni hace CORS. Confía en las cabeceras que inyecta el GW:
- Usuario: `X-User-Id` (UUID) + `X-User-Permission` (códigos separados por coma). Cada código es una autoridad.
- Servicio (ruta Interna): `X-Service-Name` = `ms01`…`ms08`.
- Sin cabeceras → 401. Cabeceras inválidas → 401. Sin permiso → 403.
- Errores en formato 7.1 `{status, codigo, mensaje, errores, correlation_id}`.

## Pruebas
```bash
mvn clean verify   # unitarias + integración (Docker) + cobertura JaCoCo >= 80 %
```
