package com.firefly.warehouse;

import com.firefly.common.api.ApiResponse;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.stream.Collectors;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException e){return ResponseEntity.status(e.code).body(ApiResponse.fail(e.code,e.getMessage()));}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> validation(MethodArgumentNotValidException e){String message=e.getBindingResult().getFieldErrors().stream().map(FieldError::getField).distinct().collect(Collectors.joining(", "));return ApiResponse.fail(400,"请求字段校验失败："+message);}

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> malformed(HttpMessageNotReadableException e){return ApiResponse.fail(400,"请求 JSON 格式或字段类型不正确");}

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiResponse<Void> integrity(DataIntegrityViolationException e){return ApiResponse.fail(409,"数据已被其他请求创建或不满足业务约束，请刷新后重试");}

    @ExceptionHandler(ConcurrencyFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiResponse<Void> concurrency(ConcurrencyFailureException e){return ApiResponse.fail(409,"库存正在被其他操作修改，请稍后重试");}
}
