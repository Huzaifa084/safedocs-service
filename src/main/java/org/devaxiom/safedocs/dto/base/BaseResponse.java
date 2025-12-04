package org.devaxiom.safedocs.dto.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.devaxiom.safedocs.enums.APIActionCode;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T> {
    private Boolean success;
    private String message;
    private APIActionCode actionCode;
    private T data;
    private Map<String, Object> meta;
    private Paginator paginator;
    private List<ErrorDetail> errors;
    private Object debug;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Paginator {
        private int currentPage;
        private long totalItems;
        private int totalPages; // TODO auto Set totalPages
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String code;
        private String field;
        private String message;
    }
}