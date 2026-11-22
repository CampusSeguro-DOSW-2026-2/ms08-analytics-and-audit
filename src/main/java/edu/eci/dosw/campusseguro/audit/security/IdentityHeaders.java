package edu.eci.dosw.campusseguro.audit.security;

/** Technical headers exchanged with the API Gateway (integration standards, section 4). */
public final class IdentityHeaders {

    public static final String USER_ID = "X-User-Id";
    /** Informative only: roles are dynamic data. Authorization uses the permissions. */
    public static final String USER_ROLE = "X-User-Role";
    /** Effective permissions of the user, comma separated. */
    public static final String USER_PERMISSION = "X-User-Permission";
    /** Calling service (ms01..ms08); the Gateway injects it on internal routes. */
    public static final String SERVICE_NAME = "X-Service-Name";

    private IdentityHeaders() {
    }
}
