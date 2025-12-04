package org.devaxiom.safedocs.dto.base;

import lombok.Setter;
import org.devaxiom.safedocs.dto.base.BaseResponse.ErrorDetail;
import org.devaxiom.safedocs.dto.base.BaseResponse.Paginator;
import org.devaxiom.safedocs.enums.APIActionCode;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ResponseBuilder {

    @Setter
    public static boolean isProd = false;

    private ResponseBuilder() {
    }

    public static <T> BaseResponseEntity<T> success() {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(true)
                .message("OK")
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<T> success(T data) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<T> success(String message) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<T> success(T data, String message) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<T> success(T data, APIActionCode actionCode) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(true)
                .message("OK")
                .actionCode(actionCode)
                .data(data)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<T> success(
            T data,
            String message,
            APIActionCode actionCode
    ) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(true)
                .message(message)
                .actionCode(actionCode)
                .data(data)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<T> success(
            T data,
            String message,
            APIActionCode actionCode,
            Map<String, Object> meta
    ) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(true)
                .message(message)
                .actionCode(actionCode)
                .data(data)
                .meta(meta)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<T> success(
            T data,
            String message,
            APIActionCode actionCode,
            BaseResponse.Paginator paginator
    ) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(true)
                .message(message)
                .actionCode(actionCode)
                .data(data)
                .paginator(paginator)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<T> success(
            T data,
            String message,
            APIActionCode actionCode,
            Map<String, Object> meta,
            BaseResponse.Paginator paginator
    ) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(true)
                .message(message)
                .actionCode(actionCode)
                .data(data)
                .meta(meta)
                .paginator(paginator)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<T> success(T data, HttpHeaders headers) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(true)
                .message("OK")
                .data(data)
                .build();
        return BaseResponseEntity.ok(body, headers);
    }

    public static <T> BaseResponseEntity<T> ok(T data) {
        return success(data);
    }

    public static <T> BaseResponseEntity<T> ok(T data, String message) {
        return success(data, message);
    }

    public static <T> BaseResponseEntity<List<T>> success(
            List<T> data,
            int currentPage,
            long totalItems,
            int totalPages
    ) {
        BaseResponse.Paginator paginator = new BaseResponse.Paginator(currentPage, totalItems, totalPages);
        BaseResponse<List<T>> body = BaseResponse.<List<T>>builder()
                .success(true)
                .message("OK")
                .data(data)
                .paginator(paginator)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T, R> BaseResponseEntity<List<R>> success(
            List<T> sourceData,
            Function<T, R> mapper,
            int currentPage,
            long totalItems,
            int totalPages
    ) {
        List<R> dtos = sourceData.stream().map(mapper).toList();
        Paginator paginator = new Paginator(currentPage, totalItems, totalPages);
        BaseResponse<List<R>> body = BaseResponse.<List<R>>builder()
                .success(true)
                .message("OK")
                .data(dtos)
                .paginator(paginator)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<List<T>> success(
            List<T> data,
            int currentPage,
            long totalItems,
            int totalPages,
            String message,
            APIActionCode actionCode
    ) {
        Paginator paginator = new Paginator(currentPage, totalItems, totalPages);
        BaseResponse<List<T>> body = BaseResponse.<List<T>>builder()
                .success(true)
                .message(message)
                .actionCode(actionCode)
                .data(data)
                .paginator(paginator)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T, R> BaseResponseEntity<List<R>> success(
            List<T> sourceData,
            Function<T, R> mapper,
            int currentPage,
            long totalItems,
            int totalPages,
            String message,
            APIActionCode actionCode,
            Map<String, Object> meta
    ) {
        List<R> dtos = sourceData.stream().map(mapper).toList();
        Paginator paginator = new Paginator(currentPage, totalItems, totalPages);
        BaseResponse<List<R>> body = BaseResponse.<List<R>>builder()
                .success(true)
                .message(message)
                .actionCode(actionCode)
                .data(dtos)
                .meta(meta)
                .paginator(paginator)
                .build();
        return BaseResponseEntity.ok(body);
    }

    public static <T> BaseResponseEntity<T> error() {
        ErrorDetail defaultErr = new ErrorDetail("ERR", null, "Error");
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message("Error")
                .errors(List.of(defaultErr))
                .build();
        return BaseResponseEntity.badRequest(body);
    }

    public static <T> BaseResponseEntity<T> error(String message) {
        ErrorDetail err = new ErrorDetail("ERR", null, message);
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(List.of(err))
                .build();
        return BaseResponseEntity.badRequest(body);
    }

    public static <T> BaseResponseEntity<T> error(
            String message,
            APIActionCode actionCode
    ) {
        ErrorDetail err = new ErrorDetail(actionCode.name(), null, message);
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(actionCode)
                .errors(List.of(err))
                .build();
        return BaseResponseEntity.badRequest(body);
    }

    public static <T> BaseResponseEntity<T> error(
            String message,
            APIActionCode actionCode,
            List<ErrorDetail> errors
    ) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(actionCode)
                .errors(errors)
                .build();
        return BaseResponseEntity.badRequest(body);
    }

    public static <T> BaseResponseEntity<T> error(
            String message,
            APIActionCode actionCode,
            List<ErrorDetail> errors,
            Exception ex
    ) {
        Object debug = null;
        if (!isProd && ex != null) {
            debug = Map.<String, Object>of(
                    "internalCode", ex.getClass().getSimpleName(),
                    "stackTrace", ex.getStackTrace().length > 3
                            ? Arrays.copyOf(ex.getStackTrace(), 3)
                            : ex.getStackTrace());
        }
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(actionCode)
                .errors(errors)
                .debug(debug)
                .build();
        return BaseResponseEntity.badRequest(body);
    }

    public static <T> BaseResponseEntity<T> error(
            String message,
            APIActionCode actionCode,
            List<ErrorDetail> errors,
            Object debug,
            Map<String, Object> meta
    ) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(actionCode)
                .errors(errors)
                .debug(debug)
                .meta(meta)
                .build();
        return BaseResponseEntity.badRequest(body);
    }

    public static <T> BaseResponseEntity<T> error(
            String message,
            APIActionCode actionCode,
            List<ErrorDetail> errors,
            Object debug,
            Map<String, Object> meta,
            Paginator paginator
    ) {
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(actionCode)
                .errors(errors)
                .debug(debug)
                .meta(meta)
                .paginator(paginator)
                .build();
        return BaseResponseEntity.badRequest(body);
    }

    public static <T> BaseResponseEntity<T> simpleError(String code, String message) {
        ErrorDetail err = new ErrorDetail(code, null, message);
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(List.of(err))
                .build();
        return BaseResponseEntity.badRequest(body);
    }

    public static <T> BaseResponseEntity<T> unauthorized(String message) {
        ErrorDetail err = new ErrorDetail(APIActionCode.ATH401.name(), null, message);
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(APIActionCode.ATH401)
                .errors(List.of(err))
                .build();
        return BaseResponseEntity.unauthorized(body);
    }

    public static <T> BaseResponseEntity<T> unauthorized(String message, HttpHeaders headers) {
        ErrorDetail err = new ErrorDetail(APIActionCode.ATH401.name(), null, message);

        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(APIActionCode.ATH401)
                .errors(List.of(err))
                .build();

        return new BaseResponseEntity<>(body, headers, HttpStatus.UNAUTHORIZED);
    }

    public static <T> BaseResponseEntity<T> forbidden(String message) {
        ErrorDetail err = new ErrorDetail(APIActionCode.FOR403.name(), null, message);
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(APIActionCode.FOR403)
                .errors(List.of(err))
                .build();
        return BaseResponseEntity.forbidden(body);
    }

    public static <T> BaseResponseEntity<T> conflict(String field, String message) {
        ErrorDetail err = new ErrorDetail(APIActionCode.DUP409.name(), field, message);
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(APIActionCode.DUP409)
                .errors(List.of(err))
                .build();
        return BaseResponseEntity.conflict(body);
    }

    public static <T> BaseResponseEntity<T> notFound(String field, String message) {
        ErrorDetail err = new ErrorDetail(APIActionCode.NFD404.name(), field, message);
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(APIActionCode.NFD404)
                .errors(List.of(err))
                .build();
        return BaseResponseEntity.notFound(body);
    }

    public static <T> BaseResponseEntity<T> badRequest(String field, String message) {
        ErrorDetail err = new ErrorDetail(APIActionCode.BAD400.name(), field, message);
        BaseResponse<T> body = BaseResponse.<T>builder()
                .success(false)
                .message(message)
                .actionCode(APIActionCode.BAD400)
                .errors(List.of(err))
                .build();
        return BaseResponseEntity.badRequest(body);
    }

    public static <T> BaseResponse<List<T>> successWithPagination(
            List<T> data,
            String message,
            Page<?> page
    ) {
        BaseResponse<List<T>> response = new BaseResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);

        BaseResponse.Paginator paginator = new BaseResponse.Paginator();
        paginator.setCurrentPage(page.getNumber());
        paginator.setTotalItems(page.getTotalElements());
        paginator.setTotalPages(page.getTotalPages());
        response.setPaginator(paginator);

        return response;
    }

}
