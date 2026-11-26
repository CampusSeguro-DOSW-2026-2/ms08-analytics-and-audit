# Calidad de Software – MS08

Este documento presenta las evidencias del proceso de aseguramiento de calidad del microservicio **Analítica y Auditoría (MS08)**, perteneciente al proyecto CampusSeguro.

## 1. Pruebas automatizadas

Se implementaron y ejecutaron pruebas automatizadas para verificar el correcto funcionamiento de los componentes del microservicio. Se utilizaron dos herramientas de Maven para ejecutar las pruebas:

### 1.1. Pruebas unitarias – Surefire

Las pruebas unitarias permiten verificar el comportamiento de componentes individuales del microservicio.

**Resultados obtenidos:**

- Pruebas ejecutadas: **111**.
- Fallos: **0**.
- Errores: **0**.
- Pruebas omitidas: **0**.

**Evidencia de ejecución:**



### 1.2. Pruebas de integración – Failsafe

Las pruebas de integración permiten verificar el funcionamiento conjunto de los componentes del microservicio y sus interacciones.

**Resultados obtenidos:**

- Pruebas ejecutadas: **36**.
- Fallos: **0**.
- Errores: **0**.
- Pruebas omitidas: **0**.

**Evidencia de ejecución:**



En total, se ejecutaron **147 pruebas automatizadas**, todas finalizadas sin fallos ni errores.

## 2. Cobertura de código – JaCoCo

Se utilizó **JaCoCo 0.8.12** para medir la cobertura del código alcanzada mediante las pruebas automatizadas.

**Resultados obtenidos:**

- Cobertura de instrucciones: **98 %**.
- Cobertura de ramas: **87 %**.
- Umbral mínimo configurado: **80 % de cobertura de líneas**.
- Resultado de la validación: **Cumplido**.

El reporte permite identificar qué partes del código fueron ejecutadas durante las pruebas automatizadas y comprobar el cumplimiento del umbral de cobertura configurado.

**Evidencia del reporte de cobertura:**



## 4. Conclusión

Los resultados evidencian que MS08 cuenta con pruebas unitarias y de integración automatizadas, además de medición de cobertura mediante JaCoCo. Estas verificaciones contribuyen a evaluar la calidad y confiabilidad del microservicio.
