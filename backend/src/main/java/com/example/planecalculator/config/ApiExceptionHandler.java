package com.example.planecalculator.config;

import com.example.planecalculator.planning.PlanningException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PlanningException.class)
    public ResponseEntity<Map<String, String>> planning(PlanningException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Dados do caso incompletos ou inválidos."));
    }
}
