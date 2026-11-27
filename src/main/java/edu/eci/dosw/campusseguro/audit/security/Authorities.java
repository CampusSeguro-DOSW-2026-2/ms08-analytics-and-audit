package edu.eci.dosw.campusseguro.audit.security;

/** Authority names used by the endpoints. Permission codes are used as authorities as they come. */
public final class Authorities {

    /** Granted to a request that comes from another microservice (internal route). */
    public static final String SERVICE = "SERVICE";

    /** Audit of drills (SIMULACRO). */
    public static final String AUDIT_DRILL_READ = "ms08:audit_drill:read";
    /** Audit of real events (REAL). */
    public static final String AUDIT_REAL_EVENT_READ = "ms08:audit_real_event:read";
    public static final String INDICATORS_DRILL_READ = "ms08:indicators_drill:read";
    public static final String INDICATORS_REAL_EVENT_READ = "ms08:indicators_real_event:read";

    private Authorities() {
    }
}
