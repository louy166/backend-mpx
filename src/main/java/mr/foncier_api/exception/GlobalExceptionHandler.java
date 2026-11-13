package mr.foncier_api.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestionnaire centralisé des erreurs (Contrat API §12, EX-404 bilingue fr/ar, EX-405 zéro fuite technique).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        boolean isArabic = isArabic(request);
        Map<String, List<String>> details = new LinkedHashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String fieldName = toSnakeCase(fieldError.getField());
            details.computeIfAbsent(fieldName, k -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }

        String mainMessage = isArabic
                ? "بيانات غير صالحة"
                : (ex.getBindingResult().getFieldError() != null
                    ? ex.getBindingResult().getFieldError().getDefaultMessage()
                    : "Données invalides");

        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(new ApiErrorResponse(ErrorCode.VALIDATION_ERROR.name(), mainMessage, details));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        boolean isArabic = isArabic(request);
        String paramName = toSnakeCase(ex.getName());
        String msg = isArabic
                ? "تنسيق غير صالح للمعلمة '" + paramName + "'"
                : "Format invalide pour le paramètre '" + paramName + "'";
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(new ApiErrorResponse(
                        ErrorCode.VALIDATION_ERROR.name(),
                        msg,
                        Map.of(paramName, List.of("Format invalide"))
                ));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request
    ) {
        boolean isArabic = isArabic(request);
        String partName = ex.getRequestPartName();
        String msg = isArabic
                ? "الملف '" + partName + "' مطلوب"
                : "Le fichier '" + partName + "' est requis";
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(new ApiErrorResponse(
                        ErrorCode.VALIDATION_ERROR.name(),
                        msg,
                        Map.of(partName, List.of("Ce fichier est requis"))
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        boolean isArabic = isArabic(request);
        String paramName = ex.getParameterName();
        String msg = isArabic
                ? "الحقل '" + paramName + "' مطلوب"
                : "Le champ '" + paramName + "' est requis";
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(new ApiErrorResponse(
                        ErrorCode.VALIDATION_ERROR.name(),
                        msg,
                        Map.of(paramName, List.of("Ce champ est requis"))
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        boolean isArabic = isArabic(request);
        String msg = isArabic ? "جسم الطلب (JSON) مفقود أو غير صالح" : "Corps de requête JSON manquant ou mal formé";
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(new ApiErrorResponse(ErrorCode.VALIDATION_ERROR.name(), msg, null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        boolean isArabic = isArabic(request);
        String msg = isArabic ? "المورد غير موجود" : "Ressource inexistante : " + request.getRequestURI();
        return ResponseEntity
                .status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(new ApiErrorResponse(ErrorCode.NOT_FOUND.name(), msg, null));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request
    ) {
        boolean isArabic = isArabic(request);
        String msg = isArabic && ex.getMessageAr() != null ? ex.getMessageAr() : ex.getMessageFr();

        HttpHeaders headers = new HttpHeaders();
        if (ex.getRetryAfterSeconds() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .headers(headers)
                .body(new ApiErrorResponse(ex.getErrorCode().name(), msg, ex.getDetails()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        boolean isArabic = isArabic(request);
        String msg = isArabic ? "ليس لديك الصلاحية الكافية" : "Rôle insuffisant pour effectuer cette action";
        return ResponseEntity
                .status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(new ApiErrorResponse(ErrorCode.FORBIDDEN.name(), msg, null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request
    ) {
        boolean isArabic = isArabic(request);
        String msg = isArabic ? "حجم الملف يتجاوز الحد الأقصى المسموح به (10 ميجابايت)" : "La taille du fichier dépasse la limite maximale autorisée (10 Mo)";
        return ResponseEntity
                .status(ErrorCode.FILE_TOO_LARGE.getHttpStatus())
                .body(new ApiErrorResponse(ErrorCode.FILE_TOO_LARGE.name(), msg, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Erreur interne non gérée sur {} {}", request.getMethod(), request.getRequestURI(), ex);
        boolean isArabic = isArabic(request);
        String msg = isArabic ? "حدث خطأ داخلي في الخادم" : "Une erreur interne est survenue";
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
                .body(new ApiErrorResponse(ErrorCode.INTERNAL_ERROR.name(), msg, null));
    }

    private boolean isArabic(HttpServletRequest request) {
        String lang = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        return lang != null && lang.toLowerCase().startsWith("ar");
    }

    private String toSnakeCase(String input) {
        if (input == null) return "";
        return input.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }
}
