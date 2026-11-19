package edu.eci.dosw.campusseguro.audit.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Checks how the two indicator permissions decide what a user can read. */
class IndicatorVisibilityTest {

    private IndicatorVisibility of(String... permissions) {
        return IndicatorVisibility.of(List.of(permissions).stream().map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void drillPermissionReadsOnlyDrills() {
        IndicatorVisibility visibility = of(Authorities.INDICATORS_DRILL_READ);

        assertTrue(visibility.any());
        assertTrue(visibility.canRead(EventKind.SIMULACRO));
        assertFalse(visibility.canRead(EventKind.REAL));
    }

    @Test
    void realEventPermissionReadsOnlyRealEvents() {
        IndicatorVisibility visibility = of(Authorities.INDICATORS_REAL_EVENT_READ);

        assertTrue(visibility.canRead(EventKind.REAL));
        assertFalse(visibility.canRead(EventKind.SIMULACRO));
    }

    @Test
    void auditPermissionsDoNotGiveIndicators() {
        IndicatorVisibility visibility = of(Authorities.AUDIT_DRILL_READ, Authorities.AUDIT_REAL_EVENT_READ);

        assertFalse(visibility.any());
        assertFalse(visibility.canRead(EventKind.SIMULACRO));
    }

    @Test
    void aMissingKindIsNeverReadable() {
        assertFalse(of(Authorities.INDICATORS_DRILL_READ, Authorities.INDICATORS_REAL_EVENT_READ).canRead(null));
    }
}
