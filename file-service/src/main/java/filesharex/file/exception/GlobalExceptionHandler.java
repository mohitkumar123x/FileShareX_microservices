package filesharex.file.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler
{

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex)
    {
        log.error("RuntimeException occurred", ex);
        Map<String, Object> data = new HashMap<>();
        data.put("status", HttpStatus.BAD_REQUEST);
        data.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(data);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex)
    {
        log.error("Exception occurred", ex);
        Map<String, Object> data = new HashMap<>();
        data.put("status", HttpStatus.INTERNAL_SERVER_ERROR);
        data.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(data);
    }
}
