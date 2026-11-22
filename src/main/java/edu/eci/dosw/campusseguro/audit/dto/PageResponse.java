package edu.eci.dosw.campusseguro.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Page of results in the format of the API standards. */
public record PageResponse<T>(
        List<T> contenido,
        int page,
        int size,
        @JsonProperty("total_elementos") long totalElementos,
        @JsonProperty("total_paginas") int totalPaginas) {
}
