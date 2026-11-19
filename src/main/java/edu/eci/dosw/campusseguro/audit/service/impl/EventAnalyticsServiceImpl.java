package edu.eci.dosw.campusseguro.audit.service.impl;

import edu.eci.dosw.campusseguro.audit.service.EventAnalyticsService;
import edu.eci.dosw.campusseguro.audit.service.EventFactsLoader;
import edu.eci.dosw.campusseguro.audit.service.EventIndicatorCalculator;
import edu.eci.dosw.campusseguro.audit.service.LocationAnalyticsCalculator;
import edu.eci.dosw.campusseguro.audit.dto.EventComparisonResponse;
import edu.eci.dosw.campusseguro.audit.dto.EventComparisonResponse.Change;
import edu.eci.dosw.campusseguro.audit.dto.EventComparisonResponse.Row;
import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse;
import edu.eci.dosw.campusseguro.audit.dto.EventIndicatorsResponse.TimeStat;
import edu.eci.dosw.campusseguro.audit.dto.LocationAnalyticsResponse;
import edu.eci.dosw.campusseguro.audit.dto.LocationAnalyticsResponse.ByEvent;
import edu.eci.dosw.campusseguro.audit.dto.LocationAnalyticsResponse.Location;
import edu.eci.dosw.campusseguro.audit.exception.InvalidFieldException;
import edu.eci.dosw.campusseguro.audit.security.IndicatorVisibility;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Loads the events asked (permission checked for each one) and builds the comparison or the breakdown. */
@Service
public class EventAnalyticsServiceImpl implements EventAnalyticsService {

    static final int MIN_COMPARE = 2;
    static final int MAX_EVENTS = 10;

    private final EventFactsLoader loader;
    private final EventIndicatorCalculator indicatorCalculator;
    private final LocationAnalyticsCalculator locationCalculator;
    private final Clock clock;

    public EventAnalyticsServiceImpl(
            EventFactsLoader loader,
            EventIndicatorCalculator indicatorCalculator,
            LocationAnalyticsCalculator locationCalculator,
            Clock clock) {
        this.loader = loader;
        this.indicatorCalculator = indicatorCalculator;
        this.locationCalculator = locationCalculator;
        this.clock = clock;
    }

    @Override
    public EventComparisonResponse compare(List<String> eventIds, IndicatorVisibility visibility) {
        List<EventFactsLoader.EventFacts> events = loadAll(eventIds, visibility, MIN_COMPARE);
        Instant now = clock.instant();
        List<EventIndicatorsResponse> indicators = new ArrayList<>(events.stream()
                .map(e -> indicatorCalculator.compute(e.eventId(), e.kind(), e.facts(), now))
                .toList());
        // Oldest start first; events that never started go last.
        indicators.sort(Comparator
                .comparing(EventIndicatorsResponse::inicioEvento, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EventIndicatorsResponse::eventoId));

        List<Row> rows = new ArrayList<>();
        Row previous = null;
        for (EventIndicatorsResponse indicator : indicators) {
            Row row = row(indicator, previous);
            rows.add(row);
            previous = row;
        }
        return new EventComparisonResponse(rows);
    }

    @Override
    public LocationAnalyticsResponse locations(List<String> eventIds, IndicatorVisibility visibility) {
        List<EventFactsLoader.EventFacts> events = loadAll(eventIds, visibility, 1);
        Map<String, PlaceTotal> totals = new LinkedHashMap<>();
        for (EventFactsLoader.EventFacts event : events) {
            for (LocationAnalyticsCalculator.PlaceStat stat : locationCalculator.compute(event.facts())) {
                totals.computeIfAbsent(stat.type() + ":" + stat.id(), k -> new PlaceTotal(stat.type(), stat.id()))
                        .add(event.eventId(), stat);
            }
        }
        List<Location> places = totals.values().stream()
                .map(PlaceTotal::toLocation)
                .sorted(Comparator
                        .comparing((Location l) -> l.hastaPrimeraAtencion().promedioSegundos(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Comparator.comparingLong(Location::incidentes).reversed())
                        .thenComparing(Location::ubicacionTipo)
                        .thenComparing(Location::ubicacionId))
                .toList();
        return new LocationAnalyticsResponse(places);
    }

    /** Validates the list (UUIDs, no duplicates, size), loads each event and requires one single kind. */
    private List<EventFactsLoader.EventFacts> loadAll(
            List<String> eventIds, IndicatorVisibility visibility, int minimum) {
        loader.requireAnyPermission(visibility);
        Set<String> ids = new LinkedHashSet<>();
        if (eventIds != null) {
            eventIds.forEach(id -> ids.add(loader.normalize(id)));
        }
        if (ids.size() < minimum || ids.size() > MAX_EVENTS) {
            throw new InvalidFieldException("evento_id",
                    "Indica entre " + minimum + " y " + MAX_EVENTS + " eventos distintos");
        }
        List<EventFactsLoader.EventFacts> events = ids.stream().map(id -> loader.load(id, visibility)).toList();
        if (events.stream().map(EventFactsLoader.EventFacts::kind).distinct().count() > 1) {
            throw new InvalidFieldException("evento_id", "Los eventos deben ser del mismo tipo");
        }
        return events;
    }

    private Row row(EventIndicatorsResponse i, Row previous) {
        Double checkin = i.tiempos().hastaCheckin().promedioSegundos();
        Double evacuation = i.tiempos().evacuacion().promedioSegundos();
        Double attention = i.tiempos().hastaPrimeraAtencion().promedioSegundos();
        Change change = previous == null ? null : new Change(
                diff(i.participacion().porcentajeParticipacion(), previous.porcentajeParticipacion()),
                diff(i.notificacion().porcentajeConfirmacion(), previous.porcentajeConfirmacion()),
                diff(checkin, previous.hastaCheckinSegundos()),
                diff(evacuation, previous.evacuacionSegundos()),
                diff(attention, previous.hastaPrimeraAtencionSegundos()));
        return new Row(i.eventoId(), i.tipoEvento(), i.inicioEvento(),
                i.participacion().participantes(), i.participacion().porcentajeParticipacion(),
                i.notificacion().porcentajeConfirmacion(), i.participacion().porcentajeAsistencia(),
                i.incidentes().total(), checkin, evacuation, attention, change);
    }

    private static Double diff(Double current, Double previous) {
        return current == null || previous == null ? null : Math.round((current - previous) * 10.0) / 10.0;
    }

    /** Running total of one place over the events asked. */
    private static final class PlaceTotal {
        private final String type;
        private final String id;
        private long incidents;
        private long assistance;
        private long seconds;
        private long samples;
        private final List<ByEvent> byEvent = new ArrayList<>();

        PlaceTotal(String type, String id) {
            this.type = type;
            this.id = id;
        }

        void add(String eventId, LocationAnalyticsCalculator.PlaceStat stat) {
            incidents += stat.incidents();
            assistance += stat.assistanceRequests();
            seconds += stat.attentionSeconds();
            samples += stat.samples();
            byEvent.add(new ByEvent(eventId, stat.incidents(), stat.assistanceRequests(),
                    average(stat.attentionSeconds(), stat.samples()), stat.samples()));
        }

        Location toLocation() {
            return new Location(type, id, incidents, assistance,
                    new TimeStat(average(seconds, samples), samples), List.copyOf(byEvent));
        }

        private static Double average(long sum, long count) {
            return count == 0 ? null : Math.round(sum * 10.0 / count) / 10.0;
        }
    }
}
