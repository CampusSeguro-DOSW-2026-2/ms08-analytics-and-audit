package edu.eci.dosw.campusseguro.audit.security;

import edu.eci.dosw.campusseguro.audit.entity.EventKind;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

/**
 * Which audit records a user may read, decided by the two separate permissions:
 * <ul>
 *   <li>{@code ms08:audit_drill:read} gives the records of drills (SIMULACRO);</li>
 *   <li>{@code ms08:audit_real_event:read} gives the records of real events (REAL);</li>
 *   <li>records without kind (administrative, for example a login) need both.</li>
 * </ul>
 */
public record AuditVisibility(boolean drill, boolean real) {

    /** Reads the permissions from the authorities of the request. */
    public static AuditVisibility of(Collection<? extends GrantedAuthority> authorities) {
        boolean drill = false;
        boolean real = false;
        for (GrantedAuthority authority : authorities) {
            drill |= Authorities.AUDIT_DRILL_READ.equals(authority.getAuthority());
            real |= Authorities.AUDIT_REAL_EVENT_READ.equals(authority.getAuthority());
        }
        return new AuditVisibility(drill, real);
    }

    /** True when the user has at least one of the two permissions. */
    public boolean any() {
        return drill || real;
    }

    /** True when the user may read a record of that kind ({@code null} = administrative record). */
    public boolean canRead(EventKind kind) {
        if (kind == EventKind.SIMULACRO) {
            return drill;
        }
        if (kind == EventKind.REAL) {
            return real;
        }
        return drill && real;
    }

    /** The only kind the user may see, or {@code null} when the user has both permissions. */
    public EventKind onlyKind() {
        if (drill && !real) {
            return EventKind.SIMULACRO;
        }
        if (real && !drill) {
            return EventKind.REAL;
        }
        return null;
    }
}
