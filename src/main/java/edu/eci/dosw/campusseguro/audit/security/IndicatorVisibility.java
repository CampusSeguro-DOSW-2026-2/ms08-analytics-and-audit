package edu.eci.dosw.campusseguro.audit.security;

import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/**
 * Which indicators a user may read, decided by the two separate permissions:
 * {@code ms08:indicators_drill:read} for drills (SIMULACRO) and
 * {@code ms08:indicators_real_event:read} for real events (REAL).
 */
public record IndicatorVisibility(boolean drill, boolean real) {

    /** Reads the permissions from the authorities of the request. */
    public static IndicatorVisibility of(Collection<? extends GrantedAuthority> authorities) {
        boolean drill = false;
        boolean real = false;
        for (GrantedAuthority authority : authorities) {
            drill |= Authorities.INDICATORS_DRILL_READ.equals(authority.getAuthority());
            real |= Authorities.INDICATORS_REAL_EVENT_READ.equals(authority.getAuthority());
        }
        return new IndicatorVisibility(drill, real);
    }

    /** True when the user has at least one of the two permissions. */
    public boolean any() {
        return drill || real;
    }

    /** True when the user may read the indicators of an event of that kind. */
    public boolean canRead(EventKind kind) {
        if (kind == EventKind.SIMULACRO) {
            return drill;
        }
        if (kind == EventKind.REAL) {
            return real;
        }
        return false;
    }
}
