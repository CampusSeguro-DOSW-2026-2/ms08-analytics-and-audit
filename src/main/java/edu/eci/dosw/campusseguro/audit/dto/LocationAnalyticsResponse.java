package edu.eci.dosw.campusseguro.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Incidents, assistance requests and response time by building or zone, for one or more events.
 * Sorted by the slowest average time to first attention (places without samples last).
 */
public record LocationAnalyticsResponse(List<Location> ubicaciones) {

    /** One building or zone, with the total over the events asked and the detail by event. */
    public record Location(
            @JsonProperty("ubicacion_tipo") String ubicacionTipo,
            @JsonProperty("ubicacion_id") String ubicacionId,
            long incidentes,
            @JsonProperty("solicitudes_asistencia") long solicitudesAsistencia,
            @JsonProperty("hasta_primera_atencion") EventIndicatorsResponse.TimeStat hastaPrimeraAtencion,
            @JsonProperty("por_evento") List<ByEvent> porEvento) {
    }

    /** Numbers of one place in one event. */
    public record ByEvent(
            @JsonProperty("evento_id") String eventoId,
            long incidentes,
            @JsonProperty("solicitudes_asistencia") long solicitudesAsistencia,
            @JsonProperty("promedio_segundos") Double promedioSegundos,
            long muestras) {
    }
}
