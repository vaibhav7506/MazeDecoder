package dev.mazedecoder.server;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static dev.mazedecoder.server.ApiModels.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MazeStore.MissingMazeException.class)
    ResponseEntity<ApiError> missing(MazeStore.MissingMazeException exception) {
        return ResponseEntity.status(404).body(new ApiError("MAZE_NOT_FOUND", exception.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", exception.getMessage()));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> invalidJson() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Malformed JSON or invalid field value"));
    }
}
