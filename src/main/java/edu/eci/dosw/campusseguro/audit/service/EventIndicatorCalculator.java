package edu.eci.dosw.campusseguro.audit.service;

import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse;
import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse.Bucket;
import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse.Incidents;
import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse.Notification;
import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse.Participation;
import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse.ResponseTimes;
import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse.SlowLocation;
import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse.TimeStat;
import edu.eci.dosw.campusseguro.audit.entity.AuditEvent;
import edu.eci.dosw.campusseguro.audit.entity.AuditResult;
import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Computes the indicators of one event from its audited facts. It has no database access, so every
 * rule can be tested with plain lists. Which service sends which fact is in the facts catalog.
 */
@Component
public class EventIndicatorCalculator {

    /** Actions the calculator reads; the repository loads only these. */
    public static final Set<String> ACTIONS = Set.of(
            "REGISTRO_CHECKIN", "CAMBIO_ESTADO_CHECKIN",
            "NOTIFICATION_SENT", "NOTIFICATION_FAILED", "CONFIRM",
            "CREAR_INCIDENTE", "INICIAR_ATENCION",
            "EVENT_STARTED");

    static final int MAX_SLOW_LOCATIONS = 10;
    private static final Set<String> CHECKIN_ACTIONS = Set.of("REGISTRO_CHECKIN", "CAMBIO_ESTADO_CHECKIN");
    private static final Set<String> NOTIFICATION_ACTIONS = Set.of("NOTIFICATION_SENT", "NOTIFICATION_FAILED");
    private static final String NO_STATE = "SIN_ESTADO";
    private static final String NO_POINT = "SIN_PUNTO";
    private static final String NO_PRIORITY = "SIN_PRIORIDAD";
    private static final Comparator<AuditEvent> BY_TIME =
            Comparator.comparing(AuditEvent::fechaHora).thenComparing(AuditEvent::id);

    /**
     * Builds the indicators.
     *
     * @param eventId      event the facts belong to
     * @param kind         SIMULACRO or REAL
     * @param facts        facts of the event (any order; other actions are ignored)
     * @param calculatedAt time of the calculation
     */
    public EventIndicatorsResponse compute(
            String eventId, EventKind kind, Collection<AuditEvent> facts, Instant calculatedAt) {
        List<AuditEvent> checkins = successful(facts, "MS04", CHECKIN_ACTIONS);
        Instant start = facts.stream()
                .filter(f -> "MS06".equals(f.modulo()) && "EVENT_STARTED".equals(f.accion()))
                .filter(f -> f.resultado() == AuditResult.EXITO)
                .map(AuditEvent::fechaHora)
                .min(Comparator.naturalOrder())
                .orElse(null);

        Notification notification = notification(facts);
        Participation participation = participation(checkins, notification.destinatarios());
        Incidents incidents = incidents(facts);
        ResponseTimes times = times(facts, checkins, start);
        return new EventIndicatorsResponse(
                eventId, kind, calculatedAt, start, notification, participation, incidents, times);
    }

    private Notification notification(Collection<AuditEvent> facts) {
        long recipients = 0;
        long sent = 0;
        long failed = 0;
        for (AuditEvent fact : facts) {
            if ("MS03".equals(fact.modulo()) && NOTIFICATION_ACTIONS.contains(fact.accion())) {
                // Both results count: NOTIFICATION_FAILED is usually sent with resultado FALLO.
                recipients += number(fact, "destinatarios");
                sent += number(fact, "enviados");
                failed += number(fact, "fallidos");
            }
        }
        Set<String> confirmed = new HashSet<>();
        for (AuditEvent fact : successful(facts, "MS03", Set.of("CONFIRM"))) {
            if (fact.usuarioId() != null) {
                confirmed.add(fact.usuarioId());
            }
        }
        return new Notification(recipients, sent, failed, confirmed.size(),
                percent(confirmed.size(), recipients));
    }

    private Participation participation(List<AuditEvent> checkins, long recipients) {
        Map<String, List<AuditEvent>> byUser = new HashMap<>();
        Set<String> needHelp = new HashSet<>();
        for (AuditEvent fact : checkins) {
            if (fact.usuarioId() == null) {
                continue;
            }
            byUser.computeIfAbsent(fact.usuarioId(), k -> new java.util.ArrayList<>()).add(fact);
            if ("NEEDS_ASSISTANCE".equals(text(fact, "estado"))) {
                needHelp.add(fact.usuarioId());
            }
        }
        Map<String, Long> states = new HashMap<>();
        Map<String, Long> points = new HashMap<>();
        long safe = 0;
        for (List<AuditEvent> userFacts : byUser.values()) {
            AuditEvent last = userFacts.stream().max(BY_TIME).orElseThrow();
            String state = orDefault(text(last, "estado"), NO_STATE);
            states.merge(state, 1L, Long::sum);
            if ("SAFE".equals(state)) {
                safe++;
                points.merge(orDefault(text(last, "punto_encuentro_id"), NO_POINT), 1L, Long::sum);
            }
        }
        long participants = byUser.size();
        return new Participation(participants, percent(participants, recipients),
                buckets(states, participants), buckets(points, safe),
                needHelp.size(), percent(needHelp.size(), participants));
    }

    private Incidents incidents(Collection<AuditEvent> facts) {
        Map<String, Long> byPriority = new HashMap<>();
        long total = 0;
        for (AuditEvent fact : successful(facts, "MS05", Set.of("CREAR_INCIDENTE"))) {
            total++;
            byPriority.merge(orDefault(text(fact, "prioridad"), NO_PRIORITY), 1L, Long::sum);
        }
        return new Incidents(total, buckets(byPriority, total));
    }

    private ResponseTimes times(Collection<AuditEvent> facts, List<AuditEvent> checkins, Instant start) {
        // First check-in and first SAFE of each user, measured from the real start of the event.
        Map<String, Instant> firstCheckin = new HashMap<>();
        Map<String, Instant> firstSafe = new HashMap<>();
        for (AuditEvent fact : checkins) {
            if (fact.usuarioId() == null) {
                continue;
            }
            firstCheckin.merge(fact.usuarioId(), fact.fechaHora(), BEFORE);
            if ("SAFE".equals(text(fact, "estado"))) {
                firstSafe.merge(fact.usuarioId(), fact.fechaHora(), BEFORE);
            }
        }
        TimeStat toCheckin = start == null ? stat(List.of()) : stat(since(start, firstCheckin.values()));
        TimeStat evacuation = start == null ? stat(List.of()) : stat(since(start, firstSafe.values()));

        // Time to first attention: INICIAR_ATENCION minus CREAR_INCIDENTE of the same incident.
        List<AttentionSample> samples = attentionSamples(facts);
        List<Long> attentionTimes = samples.stream().map(AttentionSample::seconds).toList();
        Map<String, List<Long>> byLocation = new LinkedHashMap<>();
        Map<String, String[]> locationKeys = new HashMap<>();
        for (AttentionSample sample : samples) {
            if (sample.ubicacionTipo() != null && sample.ubicacionId() != null) {
                String key = sample.ubicacionTipo() + ":" + sample.ubicacionId();
                byLocation.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(sample.seconds());
                locationKeys.put(key, new String[] {sample.ubicacionTipo(), sample.ubicacionId()});
            }
        }
        List<SlowLocation> slow = byLocation.entrySet().stream()
                .map(e -> new SlowLocation(locationKeys.get(e.getKey())[0], locationKeys.get(e.getKey())[1],
                        round(average(e.getValue())), e.getValue().size()))
                .sorted(Comparator.comparingDouble(SlowLocation::promedioSegundos).reversed()
                        .thenComparing(SlowLocation::ubicacionTipo)
                        .thenComparing(SlowLocation::ubicacionId))
                .limit(MAX_SLOW_LOCATIONS)
                .toList();
        return new ResponseTimes(toCheckin, evacuation, stat(attentionTimes), slow);
    }

    /** Time from the report of an incident to its first attention, with the place it was reported at. */
    public record AttentionSample(String ubicacionTipo, String ubicacionId, long seconds) {
    }

    /**
     * One sample per attended incident: the earliest INICIAR_ATENCION minus the earliest CREAR_INCIDENTE of
     * the same resource. Negative times (clock differences) and incidents never reported are left out.
     * The place comes from the report, or from the attention when the report has none.
     */
    public List<AttentionSample> attentionSamples(Collection<AuditEvent> facts) {
        Map<String, AuditEvent> created = new HashMap<>();
        for (AuditEvent fact : successful(facts, "MS05", Set.of("CREAR_INCIDENTE"))) {
            if (fact.recursoAfectado() != null) {
                created.merge(fact.recursoAfectado(), fact, (a, b) -> BY_TIME.compare(a, b) <= 0 ? a : b);
            }
        }
        Map<String, AuditEvent> attended = new HashMap<>();
        for (AuditEvent fact : successful(facts, "MS05", Set.of("INICIAR_ATENCION"))) {
            if (fact.recursoAfectado() != null) {
                attended.merge(fact.recursoAfectado(), fact, (a, b) -> BY_TIME.compare(a, b) <= 0 ? a : b);
            }
        }
        List<AttentionSample> samples = new java.util.ArrayList<>();
        for (Map.Entry<String, AuditEvent> entry : attended.entrySet()) {
            AuditEvent open = created.get(entry.getKey());
            if (open == null) {
                continue;
            }
            long seconds = Duration.between(open.fechaHora(), entry.getValue().fechaHora()).getSeconds();
            if (seconds >= 0) {
                samples.add(new AttentionSample(firstText("ubicacion_tipo", open, entry.getValue()),
                        firstText("ubicacion_id", open, entry.getValue()), seconds));
            }
        }
        return samples;
    }

    private static final java.util.function.BinaryOperator<Instant> BEFORE =
            (a, b) -> a.isBefore(b) ? a : b;

    private static List<AuditEvent> successful(Collection<AuditEvent> facts, String module, Set<String> actions) {
        return facts.stream()
                .filter(f -> module.equals(f.modulo()) && actions.contains(f.accion()))
                .filter(f -> f.resultado() == AuditResult.EXITO)
                .toList();
    }

    /** Seconds between the start and each instant; instants before the start are left out. */
    private static List<Long> since(Instant start, Collection<Instant> instants) {
        return instants.stream()
                .map(i -> Duration.between(start, i).getSeconds())
                .filter(s -> s >= 0)
                .toList();
    }

    private static TimeStat stat(List<Long> seconds) {
        return seconds.isEmpty() ? new TimeStat(null, 0) : new TimeStat(round(average(seconds)), seconds.size());
    }

    private static double average(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    /** Groups sorted by count (then key), each with its percentage of {@code total}. */
    private static List<Bucket> buckets(Map<String, Long> counts, long total) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new Bucket(e.getKey(), e.getValue(), percent(e.getValue(), total)))
                .toList();
    }

    private static Double percent(long part, long total) {
        return total <= 0 ? null : round(part * 100.0 / total);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static long number(AuditEvent fact, String key) {
        Object value = fact.atributos() == null ? null : fact.atributos().get(key);
        return value instanceof Number n ? n.longValue() : 0;
    }

    private static String text(AuditEvent fact, String key) {
        Object value = fact.atributos() == null ? null : fact.atributos().get(key);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static String firstText(String key, AuditEvent first, AuditEvent second) {
        String value = text(first, key);
        return value != null ? value : text(second, key);
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
