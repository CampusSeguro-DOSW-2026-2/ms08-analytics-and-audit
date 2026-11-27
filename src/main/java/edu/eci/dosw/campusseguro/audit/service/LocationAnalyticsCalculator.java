package edu.eci.dosw.campusseguro.audit.service;

import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Counts incidents, assistance requests and attention times by place for the facts of one event.
 * Places come from the attributes {@code ubicacion_tipo} and {@code ubicacion_id}; a fact without them
 * is not attributed to any place.
 */
@Component
public class LocationAnalyticsCalculator {

    private final EventIndicatorCalculator indicatorCalculator;

    public LocationAnalyticsCalculator(EventIndicatorCalculator indicatorCalculator) {
        this.indicatorCalculator = indicatorCalculator;
    }

    /** Numbers of one place in one event; the sum and the count let several events be averaged exactly. */
    public record PlaceStat(
            String type, String id, long incidents, long assistanceRequests, long attentionSeconds, long samples) {
    }

    /**
     * Stats by place for one event.
     * <ul>
     *   <li>incidents: CREAR_INCIDENTE (MS05, EXITO) reported at the place;</li>
     *   <li>assistance requests: distinct users with a check-in (MS04, EXITO) in NEEDS_ASSISTANCE at the place;</li>
     *   <li>attention time: see {@link EventIndicatorCalculator#attentionSamples}.</li>
     * </ul>
     */
    public List<PlaceStat> compute(Collection<AuditEvent> facts) {
        Map<String, long[]> numbers = new HashMap<>();          // [incidents, attentionSeconds, samples]
        Map<String, Set<String>> assisted = new HashMap<>();
        Map<String, String[]> places = new HashMap<>();

        for (AuditEvent fact : facts) {
            if (fact.resultado() != AuditResult.EXITO) {
                continue;
            }
            if ("MS05".equals(fact.modulo()) && "CREAR_INCIDENTE".equals(fact.accion())) {
                String key = place(fact, places);
                if (key != null) {
                    numbers.computeIfAbsent(key, k -> new long[3])[0]++;
                }
            } else if ("MS04".equals(fact.modulo()) && isCheckin(fact.accion())
                    && "NEEDS_ASSISTANCE".equals(text(fact, "estado")) && fact.usuarioId() != null) {
                String key = place(fact, places);
                if (key != null) {
                    assisted.computeIfAbsent(key, k -> new HashSet<>()).add(fact.usuarioId());
                    numbers.computeIfAbsent(key, k -> new long[3]);
                }
            }
        }
        for (EventIndicatorCalculator.AttentionSample sample : indicatorCalculator.attentionSamples(facts)) {
            if (sample.ubicacionTipo() == null || sample.ubicacionId() == null) {
                continue;
            }
            String key = sample.ubicacionTipo() + ":" + sample.ubicacionId();
            places.putIfAbsent(key, new String[] {sample.ubicacionTipo(), sample.ubicacionId()});
            long[] n = numbers.computeIfAbsent(key, k -> new long[3]);
            n[1] += sample.seconds();
            n[2]++;
        }
        return numbers.entrySet().stream()
                .map(e -> new PlaceStat(places.get(e.getKey())[0], places.get(e.getKey())[1], e.getValue()[0],
                        assisted.getOrDefault(e.getKey(), Set.of()).size(), e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    private static boolean isCheckin(String action) {
        return "REGISTRO_CHECKIN".equals(action) || "CAMBIO_ESTADO_CHECKIN".equals(action);
    }

    /** Key {@code tipo:id} of the place of a fact, or null when it has none. */
    private static String place(AuditEvent fact, Map<String, String[]> places) {
        String type = text(fact, "ubicacion_tipo");
        String id = text(fact, "ubicacion_id");
        if (type == null || id == null) {
            return null;
        }
        String key = type + ":" + id;
        places.putIfAbsent(key, new String[] {type, id});
        return key;
    }

    private static String text(AuditEvent fact, String key) {
        Object value = fact.atributos() == null ? null : fact.atributos().get(key);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }
}
