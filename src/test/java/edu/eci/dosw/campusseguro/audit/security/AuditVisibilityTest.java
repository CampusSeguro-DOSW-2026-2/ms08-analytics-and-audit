package edu.eci.dosw.campusseguro.audit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Checks how the two separate permissions decide what a user can read. */
class AuditVisibilityTest {

    private AuditVisibility of(String... permissions) {
        return AuditVisibility.of(List.of(permissions).stream().map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void drillPermissionSeesOnlyDrills() {
        AuditVisibility visibility = of(Authorities.AUDIT_DRILL_READ);

        assertTrue(visibility.any());
        assertTrue(visibility.canRead(EventKind.SIMULACRO));
        assertFalse(visibility.canRead(EventKind.REAL));
        assertFalse(visibility.canRead(null));
        assertEquals(EventKind.SIMULACRO, visibility.onlyKind());
    }

    @Test
    void realEventPermissionSeesOnlyRealEvents() {
        AuditVisibility visibility = of(Authorities.AUDIT_REAL_EVENT_READ);

        assertTrue(visibility.canRead(EventKind.REAL));
        assertFalse(visibility.canRead(EventKind.SIMULACRO));
        assertFalse(visibility.canRead(null));
        assertEquals(EventKind.REAL, visibility.onlyKind());
    }

    @Test
    void bothPermissionsSeeEverythingIncludingAdministrativeRecords() {
        AuditVisibility visibility = of(Authorities.AUDIT_DRILL_READ, Authorities.AUDIT_REAL_EVENT_READ);

        assertTrue(visibility.canRead(EventKind.SIMULACRO));
        assertTrue(visibility.canRead(EventKind.REAL));
        assertTrue(visibility.canRead(null));
        assertNull(visibility.onlyKind());
    }

    @Test
    void otherPermissionsGiveNothing() {
        AuditVisibility visibility = of(Authorities.INDICATORS_DRILL_READ, Authorities.SERVICE);

        assertFalse(visibility.any());
        assertFalse(visibility.canRead(EventKind.SIMULACRO));
        assertNull(visibility.onlyKind());
    }
}
