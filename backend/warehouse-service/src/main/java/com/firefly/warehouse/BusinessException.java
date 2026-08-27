package com.firefly.warehouse;

class BusinessException extends RuntimeException {
    final int code;
    BusinessException(int code,String message){super(message);this.code=code;}
}
