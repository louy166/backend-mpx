package mr.foncier_api.exception;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String messageFr;
    private final String messageAr;
    private final Map<String, List<String>> details;
    private final Integer retryAfterSeconds;

    public BusinessException(ErrorCode errorCode, String messageFr, String messageAr) {
        this(errorCode, messageFr, messageAr, null, null);
    }

    public BusinessException(
            ErrorCode errorCode,
            String messageFr,
            String messageAr,
            Map<String, List<String>> details
    ) {
        this(errorCode, messageFr, messageAr, details, null);
    }

    public BusinessException(
            ErrorCode errorCode,
            String messageFr,
            String messageAr,
            Map<String, List<String>> details,
            Integer retryAfterSeconds
    ) {
        super(messageFr);
        this.errorCode = errorCode;
        this.messageFr = messageFr;
        this.messageAr = messageAr;
        this.details = details;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getMessageFr() {
        return messageFr;
    }

    public String getMessageAr() {
        return messageAr;
    }

    public Map<String, List<String>> getDetails() {
        return details;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
