package org.devaxiom.safedocs.dto.base;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public class BaseResponseEntity<T> extends ResponseEntity<BaseResponse<T>> {

    public BaseResponseEntity(BaseResponse<T> body, HttpStatus status) {
        super(body, defaultJsonHeaders(), status);
    }

    public BaseResponseEntity(
            BaseResponse<T> body,
            HttpHeaders headers,
            HttpStatus status
    ) {
        super(body, ensureJsonContentType(headers), status);
    }

    private static HttpHeaders defaultJsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private static HttpHeaders ensureJsonContentType(HttpHeaders in) {
        HttpHeaders h = new HttpHeaders();
        if (in != null) h.putAll(in);
        if (!h.containsKey(HttpHeaders.CONTENT_TYPE)) {
            h.setContentType(MediaType.APPLICATION_JSON);
        }
        return h;
    }

    public static <T> BaseResponseEntity<T> ok(BaseResponse<T> body) {
        return new BaseResponseEntity<>(body, HttpStatus.OK);
    }

    public static <T> BaseResponseEntity<T> created(BaseResponse<T> body) {
        return new BaseResponseEntity<>(body, HttpStatus.CREATED);
    }

    public static <T> BaseResponseEntity<T> badRequest(BaseResponse<T> body) {
        return new BaseResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    public static <T> BaseResponseEntity<T> unauthorized(BaseResponse<T> body) {
        return new BaseResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    public static <T> BaseResponseEntity<T> forbidden(BaseResponse<T> body) {
        return new BaseResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    public static <T> BaseResponseEntity<T> notFound(BaseResponse<T> body) {
        return new BaseResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    public static <T> BaseResponseEntity<T> conflict(BaseResponse<T> body) {
        return new BaseResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    public static <T> BaseResponseEntity<T> internalError(BaseResponse<T> body) {
        return new BaseResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static <T> BaseResponseEntity<T> ok(BaseResponse<T> body, HttpHeaders h) {
        return new BaseResponseEntity<>(body, h, HttpStatus.OK);
    }

    public static <T> BaseResponseEntity<T> created(BaseResponse<T> body, HttpHeaders h) {
        return new BaseResponseEntity<>(body, h, HttpStatus.CREATED);
    }

    public static <T> BaseResponseEntity<T> badRequest(BaseResponse<T> body, HttpHeaders h) {
        return new BaseResponseEntity<>(body, h, HttpStatus.BAD_REQUEST);
    }

    public static <T> BaseResponseEntity<T> accepted(BaseResponse<T> body) {
        return new BaseResponseEntity<>(body, HttpStatus.ACCEPTED);
    }

    public static <T> BaseResponseEntity<T> accepted(BaseResponse<T> body, HttpHeaders h) {
        return new BaseResponseEntity<>(body, h, HttpStatus.ACCEPTED);
    }

}
