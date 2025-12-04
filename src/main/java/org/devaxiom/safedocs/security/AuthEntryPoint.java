package org.devaxiom.safedocs.security;

import org.devaxiom.safedocs.dto.base.*;
import org.devaxiom.safedocs.enums.APIActionCode;
import org.devaxiom.safedocs.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        Throwable cause = authException.getCause();
        HttpStatus status = (cause instanceof AccessDeniedException
                || cause instanceof AuthorizationDeniedException)
                ? HttpStatus.FORBIDDEN
                : HttpStatus.UNAUTHORIZED;

        String message = resolveMessage(authException);
        writeError(response, status, message, authException);
    }

    public void handleJwtException(HttpServletResponse response, Exception ex) throws IOException {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        String message = resolveMessage(ex);
        writeError(response, status, message, ex);
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String message,
            Exception ex
    ) throws IOException {
        // Build a BaseResponseEntity via your ResponseBuilder
        BaseResponseEntity<?> entity = ResponseBuilder.error(
                message,
                // map 403 vs 401
                (status == HttpStatus.FORBIDDEN ? APIActionCode.FOR403 : APIActionCode.UN_AUTH401),
                List.of(new BaseResponse.ErrorDetail(
                        status == HttpStatus.FORBIDDEN ? "FORBIDDEN" : "UNAUTHORIZED",
                        null,
                        message
                )),
                ex
        );

        response.setStatus(entity.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), entity.getBody());
    }

    private String resolveMessage(Exception ex) {
        if (ex instanceof TokenExpiredException) return "JWT token expired";
        else if (ex instanceof ExpiredJwtException) return "JWT token expired";
        else if (ex instanceof SignatureException) return "Invalid JWT signature";
        else if (ex instanceof UserNotFoundException) return "User not found";
        else if (ex instanceof InvalidTokenException) return "Invalid or revoked token";
        else if (ex instanceof BadCredentialsException) return "Bad credentials";
        else if (ex instanceof InternalAuthenticationServiceException) return ex.getMessage();
        else if (ex instanceof AccessDeniedException) return "Access denied";
        else if (ex instanceof AuthorizationDeniedException) return "Authorization denied";
        else if (ex instanceof AuthenticationException) return ex.getMessage();
        return "Authentication failed";
    }
}