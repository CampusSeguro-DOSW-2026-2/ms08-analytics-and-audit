package edu.eci.dosw.campusseguro.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import java.time.Instant;
import java.util.List;

/**
 * Side-by-side summary of several events of the same kind, oldest start first.
 * Percentages are 0-100, times are seconds; both are null when there is nothing to measure.
 */
public record EventComparisonResponse(List<Row> eventos) {

    /** Main indicators of one event and how they changed from the previous event of the list. */
    public record Row(
            @JsonProperty("evento_id") String eventoId,
            @JsonProperty("tipo_evento") EventKind tipoEvento,
            @JsonProperty("inicio_evento") Instant inicioEvento,
            long participantes,
            @JsonProperty("porcentaje_participacion") Double porcentajeParticipacion,
            @JsonProperty("porcentaje_confirmacion") Double porcentajeConfirmacion,
            @JsonProperty("porcentaje_asistencia") Double porcentajeAsistencia,
            @JsonProperty("incidentes") long incidentes,
            @JsonProperty("hasta_checkin_segundos") Double hastaCheckinSegundos,
            @JsonProperty("evacuacion_segundos") Double evacuacionSegundos,
            @JsonProperty("hasta_primera_atencion_segundos") Double hastaPrimeraAtencionSegundos,
            @JsonProperty("cambio_vs_anterior") Change cambioVsAnterior) {
    }

    /** Current minus previous value; null when either one is null. The first event has no change. */
    public record Change(
            @JsonProperty("porcentaje_participacion") Double porcentajeParticipacion,
            @JsonProperty("porcentaje_confirmacion") Double porcentajeConfirmacion,
            @JsonProperty("hasta_checkin_segundos") Double hastaCheckinSegundos,
            @JsonProperty("evacuacion_segundos") Double evacuacionSegundos,
            @JsonProperty("hasta_primera_atencion_segundos") Double hastaPrimeraAtencionSegundos) {
    }
}
