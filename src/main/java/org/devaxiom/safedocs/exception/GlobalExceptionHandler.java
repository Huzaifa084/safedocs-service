package org.devaxiom.safedocs.exception;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.devaxiom.safedocs.dto.base.BaseResponse;
import org.devaxiom.safedocs.dto.base.BaseResponseEntity;
import org.devaxiom.safedocs.dto.base.ResponseBuilder;
import org.devaxiom.safedocs.enums.APIActionCode;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400: Data integrity / bad request
    @ExceptionHandler(DataIntegrityViolationException.class)
    public BaseResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException ex) {
        String root = ExceptionUtils.getRootCauseMessage(ex);
        String msg = "Data integrity violation";
        String lower = root.toLowerCase();
        if (lower.contains("unique") || lower.contains("duplicate")) {
            msg = "Duplicate value violates unique constraint";
        } else if (lower.contains("foreign key")) {
            msg = "Related record not found (foreign key violation)";
        } else if (lower.contains("not-null") || lower.contains("null value")) {
            msg = "Required field is missing";
        }
        return errorResponse(ex, msg, APIActionCode.BAD400, "DATA_INTEGRITY_VIOLATION");
    }

    // 400: validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        log.error("Validation failed: {}", ex.getMessage(), ex);
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        Map<String, Object> body = Map.of(
                "code", "VALIDATION_ERROR",
                "message", "Invalid request parameters",
                "timestamp", Instant.now(),
                "errors", errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    // 401: pre-authentication required (OTP flow)
    @ExceptionHandler(PreAuthRequiredException.class)
    public ResponseEntity<BaseResponse<?>> handlePreAuthRequired(PreAuthRequiredException ex) {
        log.warn("Pre-authentication required: {}", ex.getMessage());

        BaseResponse.ErrorDetail detail = new BaseResponse.ErrorDetail(
                "PRE_AUTH_REQUIRED",
                null,
                ex.getMessage()
        );

        BaseResponse<?> response = BaseResponse.builder()
                .success(false)
                .message("Pre-authentication required")
                .errors(List.of(detail))
                .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-pre-auth-token", ex.getPreAuthToken())
                .body(response);
    }

    // 401: authentication problems
    @ExceptionHandler({
            AuthenticationException.class,
            InvalidTokenException.class,
            TokenExpiredException.class
    })
    public BaseResponseEntity<?> handleAuthErrors(Exception ex) {
        APIActionCode code;
        String detailCode, msg;

        if (ex instanceof InvalidTokenException) {
            code = APIActionCode.UN_AUTH401;
            detailCode = "INVALID_TOKEN";
            msg = "Invalid authentication token";
        } else if (ex instanceof TokenExpiredException) {
            code = APIActionCode.UN_AUTH401;
            detailCode = "TOKEN_EXPIRED";
            msg = "Token has expired";
        } else {
            code = APIActionCode.UN_AUTH401;
            detailCode = "AUTHENTICATION_FAILED";
            msg = "Authentication failed";
        }

        return errorResponse(ex, msg + ": " + ex.getMessage(), code, detailCode);
    }

    // 403: forbidden (covers both Spring Security and our domain-level AccessDeniedException)
    @ExceptionHandler({
            AccessDeniedException.class,
            AuthorizationDeniedException.class,
            org.devaxiom.safedocs.exception.AccessDeniedException.class
    })
    public BaseResponseEntity<?> handleAccessDenied(Exception ex) {
        String message;
        if (ex instanceof AuthorizationDeniedException) {
            message = "Insufficient permissions to access this resource";
        } else {
            message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? "Access denied" : ex.getMessage();
        }
        log.warn("ACCESS_DENIED: {}", message);
        return ResponseBuilder.forbidden(message);
    }

    // 403: explicit domain forbidden
    @ExceptionHandler(ForbiddenException.class)
    public BaseResponseEntity<?> handleForbidden(ForbiddenException ex) {
        String message = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? "Forbidden"
                : ex.getMessage();
        log.warn("FORBIDDEN: {}", message);
        return ResponseBuilder.forbidden(message);
    }

    // 404: not found
    @ExceptionHandler({
            UserNotFoundException.class,
            ResourceNotFoundException.class,
            TransactionNotFoundException.class,
            UsernameNotFoundException.class
    })
    public BaseResponseEntity<?> handleNotFound(RuntimeException ex) {
        log.warn("RESOURCE_NOT_FOUND: {}", ex.getMessage());
        return ResponseBuilder.notFound(null, ex.getMessage());
    }

    // 409: resource already exists (e.g., seeding duplicates)
    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public BaseResponseEntity<?> handleAlreadyExists(ResourceAlreadyExistsException ex) {
        String field = ex.getField();
        String message = ex.getMessage();
        return ResponseBuilder.conflict(field, message);
    }

    // 400: custom bad request
    @ExceptionHandler(BadRequestException.class)
    public BaseResponseEntity<?> handleBadRequest(BadRequestException ex) {
        String msg = ex.getMessage();
        if (msg != null && msg.startsWith("Non-grantable permissions requested")) {
            // Specialized code to allow clients to branch on subset violations
            return ResponseBuilder.simpleError("PERMISSION_SUBSET_VIOLATION", msg);
        }
        return ResponseBuilder.badRequest(null, msg);
    }

    // 400: invalid state
    @ExceptionHandler(InvalidStateException.class)
    public BaseResponseEntity<?> handleInvalidState(InvalidStateException ex) {
        return errorResponse(ex, ex.getMessage(), APIActionCode.BAD400, "INVALID_STATE");
    }

    // 400: JSON parse / enum binding errors
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponseEntity<?> handleJsonParse(HttpMessageNotReadableException ex) {
        String msg = ex.getMessage();
        String userMsg = "Malformed request body";

        // Prefer a specific, sanitized root cause to help clients fix JSON
        String root = ExceptionUtils.getRootCauseMessage(ex);
        String hint = sanitizeMessage(root);

        // Keep any special-casing first (example for RoleType)
        if (msg != null && msg.contains("Cannot deserialize value of type") && msg.contains("RoleType")) {
            String invalid = null;
            int idx = msg.indexOf('"');
            if (idx >= 0) {
                int idx2 = msg.indexOf('"', idx + 1);
                if (idx2 > idx) invalid = msg.substring(idx + 1, idx2);
            }
            userMsg = "Invalid roleType" + (invalid != null ? (": " + invalid) : "")
                    + ". Allowed: SYSTEM_OWNER, SYSTEM_ADMIN, SYSTEM_USER, AGENCY_OWNER, AGENCY_ADMIN," +
                    " AGENCY_USER, SUB_ACCOUNT_OWNER, SUB_ACCOUNT_ADMIN, SUB_ACCOUNT_USER, CUSTOMER";
        } else if (hint != null && !hint.isBlank()) {
            // Example: "Unexpected character ('{') ... was expecting double-quote ..."
            userMsg = "Malformed request body: " + hint;
        }

        return errorResponse(ex, userMsg, APIActionCode.BAD400, "JSON_PARSE_ERROR");
    }

    // 500: internal JSON (storage/serialization) errors
    @ExceptionHandler(JsonConversionException.class)
    public BaseResponseEntity<?> handleInternalJson(JsonConversionException ex) {
        String userMsg = "Internal JSON processing error";
        return errorResponse(ex, userMsg, APIActionCode.SRV500, "JSON_INTERNAL_ERROR");
    }

    // 429: plan limits
    @ExceptionHandler(PlanLimitExceededException.class)
    public BaseResponseEntity<?> handlePlanLimit(PlanLimitExceededException ex) {
        BaseResponse.ErrorDetail detail = new BaseResponse.ErrorDetail("PLAN_LIMIT_EXCEEDED", null, ex.getMessage());
        BaseResponse<?> body = BaseResponse.builder()
                .success(false)
                .message(ex.getMessage())
                .errors(List.of(detail))
                .build();
        return new BaseResponseEntity<>(body, HttpStatus.TOO_MANY_REQUESTS);
    }

    // 401: unauthorized access
    @ExceptionHandler(UnauthorizedException.class)
    public BaseResponseEntity<?> handleUnauthorized(UnauthorizedException ex) {
        log.error("Unauthorized access: {}", ex.getMessage(), ex);
        return ResponseBuilder.unauthorized("Unauthorized access: " + ex.getMessage());
    }

    // 500: notifications
    @ExceptionHandler(NotificationException.class)
    public BaseResponseEntity<?> handleNotification(NotificationException ex) {
        return errorResponse(ex, ex.getMessage(), APIActionCode.SRV500, "NOTIFICATION_ERROR");
    }

    // 500: metadata errors
    @ExceptionHandler(MetadataException.class)
    public BaseResponseEntity<?> handleMetadata(MetadataException ex) {
        return errorResponse(ex, ex.getMessage(), APIActionCode.SRV500, "METADATA_ERROR");
    }

    // 400: email errors
    @ExceptionHandler(EmailException.class)
    public BaseResponseEntity<?> handleEmail(EmailException ex) {
        return errorResponse(ex, ex.getMessage(), APIActionCode.BAD400, "EMAIL_ERROR");
    }

    // 503: database outage
    @ExceptionHandler(DataAccessException.class)
    public BaseResponseEntity<?> handleDatabase(DataAccessException ex) {
        final String errorId = UUID.randomUUID().toString();

        SQLException sqlEx = unwrapSqlException(ex);
        String sqlState = (sqlEx != null) ? sqlEx.getSQLState() : null;
        Integer vendorCode = (sqlEx != null) ? sqlEx.getErrorCode() : null;

        String root = ExceptionUtils.getRootCauseMessage(ex);
        DbCategory category = categorizePg(sqlState, root);
        HttpStatus status = httpStatusFor(category);
        APIActionCode action = apiActionFor(status);

        String userMsg = sanitizeMessage(userMessageFor(category, root, sqlState));

        log.error("DATABASE_ERROR [{}] cat={} sqlState={} vendorCode={} root='{}'",
                errorId, category, sqlState, vendorCode, root, ex);

        BaseResponse.ErrorDetail detail = new BaseResponse.ErrorDetail(
                "DATABASE_ERROR",
                null,
                userMsg
        );

        // Use a null-safe meta map; Map.of forbids nulls and was triggering NPE
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("errorId", errorId);
        meta.put("category", category.name());
        if (sqlState != null) meta.put("sqlState", sqlState);
        if (vendorCode != null) meta.put("vendorCode", vendorCode);
        meta.put("timestamp", Instant.now());

        BaseResponse<?> body = BaseResponse.builder()
            .success(false)
            .message(userMsg)
            .actionCode(action)
            .errors(List.of(detail))
            .meta(meta)
            .build();

        BaseResponseEntity<?> response = new BaseResponseEntity<>(body, status);

        if (isTransient(category))
            response.getHeaders().add("Retry-After", "5");

        response.getHeaders().add("x-error-id", errorId);
        return response;
    }

    // 404: resource / handler not found (APIs & static)
    @ExceptionHandler(NoResourceFoundException.class)
    public BaseResponseEntity<?> handleNoResourceFound(NoResourceFoundException ex) {
        String path = ex.getResourcePath();
        log.debug("Resource not found: {}", path);

        // For API paths, use our standard notFound response; for others, keep a generic message
        if (path.startsWith("/api")) {
            return ResponseBuilder.notFound(null, "Endpoint not found: " + path);
        }

        BaseResponse.ErrorDetail detail = new BaseResponse.ErrorDetail(
                "RESOURCE_NOT_FOUND",
                null,
                "The requested resource was not found"
        );
        BaseResponse<?> body = BaseResponse.builder()
                .success(false)
                .message("The requested resource was not found")
                .errors(List.of(detail))
                .meta(Map.of(
                        "path", path,
                        "suggestion", "Try /api for API endpoints or /swagger-ui/index.html for API documentation",
                        "timestamp", Instant.now()
                ))
                .build();

        return new BaseResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    // 400: missing query or form parameter
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public BaseResponseEntity<?> handleMissingRequestParam(MissingServletRequestParameterException ex) {
        String name = ex.getParameterName();
        String msg = "Required request parameter '" + name + "' is missing";
        return ResponseBuilder.badRequest(name, msg);
    }

    // 400: type mismatch for query/path parameter
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public BaseResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName();
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "value";
        Object value = ex.getValue();
        String valueStr = value != null ? value.toString() : "null";
        String msg = "Invalid value '" + valueStr + "' for parameter '" + name + "'. Expected " + expected;
        return ResponseBuilder.badRequest(name, msg);
    }

    // 500: catch-all
    @ExceptionHandler(Exception.class)
    public BaseResponseEntity<?> handleAll(Exception ex) {
        return errorResponse(ex, "An unexpected error occurred", APIActionCode.SRV500, "INTERNAL_ERROR");
    }

    // Defensive: if a converter fails due to preset non-JSON Content-Type, send minimal JSON
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<Object> handleNotWritable(HttpMessageNotWritableException ex) {
        log.warn("Response write failed: {}", ex.getMessage());
        Map<String, Object> body = Map.of(
                "code", "RESPONSE_WRITE_ERROR",
                "message", "Unable to write response",
                "timestamp", Instant.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private BaseResponseEntity<?> errorResponse(
            Exception ex,
            String message,
            APIActionCode actionCode,
            String errorCode
    ) {
        log.error("{}: {}", errorCode, ex.getMessage(), ex);
        BaseResponse.ErrorDetail detail = new BaseResponse.ErrorDetail(errorCode, null, message);
        return ResponseBuilder.error(message, actionCode, List.of(detail), ex);
    }

    private enum DbCategory {
        CONNECTION_ISSUE,              // 08xxx
        TIMEOUT,                       // statement/cancel timeouts (heuristic)
        DEADLOCK_OR_SERIALIZATION,     // 40P01, 40001
        TOO_MANY_CONNECTIONS,          // 53300
        MAINTENANCE_OR_SHUTDOWN,       // 57P01-57P03
        AUTH_OR_PERMISSION,            // 28xxx
        TX_IN_FAILED_STATE,            // 25P02
        DATA_INTEGRITY,                // 23xxx (23505 unique, 23503 fk, 23502 not-null, etc.)
        UNKNOWN
    }

    private SQLException unwrapSqlException(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof SQLException) return (SQLException) cur;
            cur = cur.getCause();
        }
        return null;
    }

    private DbCategory categorizePg(String sqlState, String message) {
        String m = (message == null) ? "" : message.toLowerCase();

        if (sqlState != null) {
            if (sqlState.startsWith("08")) return DbCategory.CONNECTION_ISSUE;
            if ("53300".equals(sqlState)) return DbCategory.TOO_MANY_CONNECTIONS;
            if (sqlState.startsWith("57")) return DbCategory.MAINTENANCE_OR_SHUTDOWN;
            if ("40P01".equals(sqlState) || "40001".equals(sqlState)) return DbCategory.DEADLOCK_OR_SERIALIZATION;
            if (sqlState.startsWith("28")) return DbCategory.AUTH_OR_PERMISSION;
            if ("25P02".equals(sqlState)) return DbCategory.TX_IN_FAILED_STATE;
            if (sqlState.startsWith("23")) return DbCategory.DATA_INTEGRITY;
        }

        // Heuristics for common Postgres phrases
        if (m.contains("canceling statement due to statement timeout")
                || m.contains("timeout") || m.contains("timed out")) return DbCategory.TIMEOUT;

        if (m.contains("could not obtain lock") || m.contains("deadlock"))
            return DbCategory.DEADLOCK_OR_SERIALIZATION;

        if (m.contains("too many connections")) return DbCategory.TOO_MANY_CONNECTIONS;

        if (m.contains("connection refused") || m.contains("could not connect")
                || m.contains("connection reset") || m.contains("broken pipe"))
            return DbCategory.CONNECTION_ISSUE;

        if (m.contains("permission denied") || m.contains("authentication"))
            return DbCategory.AUTH_OR_PERMISSION;

        if (m.contains("unique constraint") || m.contains("duplicate key") || m.contains("foreign key")
                || m.contains("not-null"))
            return DbCategory.DATA_INTEGRITY;

        return DbCategory.UNKNOWN;
    }

    private HttpStatus httpStatusFor(DbCategory c) {
        return switch (c) {
            case CONNECTION_ISSUE, MAINTENANCE_OR_SHUTDOWN -> HttpStatus.SERVICE_UNAVAILABLE; // 503
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;     // 504
            case DEADLOCK_OR_SERIALIZATION -> HttpStatus.CONFLICT;            // 409 (client can retry)
            case TOO_MANY_CONNECTIONS -> HttpStatus.TOO_MANY_REQUESTS;   // 429
            case AUTH_OR_PERMISSION -> HttpStatus.FORBIDDEN;           // 403
            case TX_IN_FAILED_STATE -> HttpStatus.CONFLICT;            // 409
            case DATA_INTEGRITY -> HttpStatus.BAD_REQUEST;         // 400
            default -> HttpStatus.INTERNAL_SERVER_ERROR;// 500
        };
    }

    private APIActionCode apiActionFor(HttpStatus status) {
        return switch (status) {
            case SERVICE_UNAVAILABLE -> APIActionCode.SRV503;
            case GATEWAY_TIMEOUT -> APIActionCode.SRV504;
            case CONFLICT -> APIActionCode.DUP409;
            case TOO_MANY_REQUESTS -> APIActionCode.TOO_MANY_429;
            case FORBIDDEN -> APIActionCode.FOR403;
            case BAD_REQUEST -> APIActionCode.BAD400;
            default -> APIActionCode.SRV500;
        };
    }

    private boolean isTransient(DbCategory c) {
        return c == DbCategory.CONNECTION_ISSUE
                || c == DbCategory.MAINTENANCE_OR_SHUTDOWN
                || c == DbCategory.TIMEOUT
                || c == DbCategory.DEADLOCK_OR_SERIALIZATION
                || c == DbCategory.TOO_MANY_CONNECTIONS;
    }

    /**
     * Build a concise, user-facing message. We avoid echoing raw SQL or URLs.
     * We still incorporate a hint of the original cause in a safe way.
     */
    private String userMessageFor(DbCategory c, String root, String sqlState) {
        return switch (c) {
            case CONNECTION_ISSUE -> "Database connection failure. Please try again shortly.";
            case MAINTENANCE_OR_SHUTDOWN -> "Database is restarting or under maintenance. Please retry in a moment.";
            case TIMEOUT -> "Database operation timed out. Please retry your request.";
            case DEADLOCK_OR_SERIALIZATION -> "Concurrent update conflict detected. Please retry the operation.";
            case TOO_MANY_CONNECTIONS ->
                    "Database has reached the maximum number of connections. Please retry shortly.";
            case AUTH_OR_PERMISSION -> "Database authentication/permission issue. Verify credentials/roles.";
            case TX_IN_FAILED_STATE -> "Transaction failed state. The operation must be retried in a new transaction.";
            case DATA_INTEGRITY -> "Request violated database constraints. Please check your input.";
            default -> {
                // For unknowns, add a sanitized hint from the root message (helps support)
                String hint = sanitizeMessage(root);
                yield (hint == null || hint.isBlank())
                        ? "Database error. Please retry. If it persists, contact support."
                        : "Database error: " + hint;
            }
        };
    }

    /* ── Sanitization: mask secrets/URLs/paths without nuking helpful words ── */

    private static final Pattern PWD_KV = Pattern.compile("(?i)(password|pwd)\\s*=\\s*([^;\\s]+)");
    private static final Pattern USER_KV = Pattern.compile("(?i)(user(name)?)\\s*=\\s*([^;\\s]+)");
    private static final Pattern JDBC_URL = Pattern.compile("(?i)jdbc:[^\\s]+");
    private static final Pattern URI = Pattern.compile("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s]+");
    private static final Pattern IP = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");
    private static final Pattern FILEPATH = Pattern.compile("([A-Za-z]:\\\\[^\\s]+|/[^\\s]+)");

    private String sanitizeMessage(String msg) {
        if (msg == null) return null;
        String s = msg;
        s = maskGroup(PWD_KV, s, 2, "***");
        s = maskGroup(USER_KV, s, 3, "***");
        s = maskAll(JDBC_URL, s, "[REDACTED-JDBC]");
        s = maskAll(URI, s, "[REDACTED-URI]");
        s = maskAll(IP, s, "[REDACTED-IP]");
        s = maskAll(FILEPATH, s, "[REDACTED-PATH]");
        if (s.length() > 400) s = s.substring(0, 400) + "...";
        return s;
    }

    private String maskAll(Pattern p, String s, String replacement) {
        return p.matcher(s).replaceAll(replacement);
    }

    private String maskGroup(Pattern p, String s, int groupToMask, String mask) {
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String repl = m.group(0).replace(m.group(groupToMask), mask);
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
