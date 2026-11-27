package edu.eci.dosw.campusseguro.audit.service;

import edu.eci.dosw.campusseguro.audit.dto.AuditEventRequest;

/** Receives audit facts from the other microservices. */
public interface AuditIngestionService {

    /**
     * Validates the contract rules and stores the fact. Sending the same fact again (same id) is
     * accepted and ignored, so the sender can retry safely.
     *
     * @param request validated request body
     * @param origin  name of the calling service (from {@code X-Service-Name})
     */
    void register(AuditEventRequest request, String origin);
}
