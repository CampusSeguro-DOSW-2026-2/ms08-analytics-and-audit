package edu.eci.dosw.campusseguro.audit.service;

import edu.eci.dosw.campusseguro.audit.dto.AuditEventResponse;
import edu.eci.dosw.campusseguro.audit.dto.AuditQuery;
import edu.eci.dosw.campusseguro.audit.dto.PageResponse;
import edu.eci.dosw.campusseguro.audit.security.AuditVisibility;

/** Read-only access to the audit records, limited by what the user is allowed to see. */
public interface AuditQueryService {

    /** Filtered page, newest first. Only records the user may read are returned. */
    PageResponse<AuditEventResponse> search(AuditQuery query, AuditVisibility visibility);

    /** One record; 404 if it does not exist, 403 if its kind is not readable by the user. */
    AuditEventResponse get(String id, AuditVisibility visibility);
}
