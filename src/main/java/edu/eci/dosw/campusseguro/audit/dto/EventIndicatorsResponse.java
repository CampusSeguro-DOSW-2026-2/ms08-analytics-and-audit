package edu.eci.dosw.campusseguro.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import java.time.Instant;
import java.util.List;

/**
 * Indicators of one event (drill or real), computed from the audited facts.
 * Percentages are 0-100 with one decimal, or null when there is nothing to divide by.
 * Durations are in seconds, or null when there are no samples.
 */
public record EventIndicatorsResponse(
        @JsonProperty("evento_id") String eventoId,
        @JsonProperty("tipo_evento") EventKind tipoEvento,
        @JsonProperty("calculado_en") Instant calculadoEn,
        @JsonProperty("inicio_evento") Instant inicioEvento,
        Notification notificacion,
        Participation participacion,
        Incidents incidentes,
        ResponseTimes tiempos) {

    /** Alerts sent for the event and how many people confirmed. */
    public record Notification(
            long destinatarios,
            long enviados,
            long fallidos,
            long confirmados,
            @JsonProperty("porcentaje_confirmacion") Double porcentajeConfirmacion) {
    }

    /** People who checked in, by last state and meeting point. */
    public record Participation(
            long participantes,
            @JsonProperty("porcentaje_participacion") Double porcentajeParticipacion,
            @JsonProperty("por_estado") List<Bucket> porEstado,
            @JsonProperty("puntos_encuentro") List<Bucket> puntosEncuentro,
            @JsonProperty("solicitaron_asistencia") long solicitaronAsistencia,
            @JsonProperty("porcentaje_asistencia") Double porcentajeAsistencia) {
    }

    /** Incidents reported during the event. */
    public record Incidents(long total, @JsonProperty("por_categoria") List<Bucket> porCategoria) {
    }

    /** Average times (seconds). */
    public record ResponseTimes(
            @JsonProperty("hasta_checkin") TimeStat hastaCheckin,
            @JsonProperty("evacuacion") TimeStat evacuacion,
            @JsonProperty("hasta_primera_atencion") TimeStat hastaPrimeraAtencion,
            @JsonProperty("ubicaciones_mas_lentas") List<SlowLocation> ubicacionesMasLentas) {
    }

    /** One group of a count with its percentage of the group total. */
    public record Bucket(String clave, long cantidad, Double porcentaje) {
    }

    /** Average time in seconds and how many samples it comes from. */
    public record TimeStat(
            @JsonProperty("promedio_segundos") Double promedioSegundos,
            long muestras) {
    }

    /** Building or zone with its average time to first attention. */
    public record SlowLocation(
            @JsonProperty("ubicacion_tipo") String ubicacionTipo,
            @JsonProperty("ubicacion_id") String ubicacionId,
            @JsonProperty("promedio_segundos") double promedioSegundos,
            long muestras) {
    }
}
