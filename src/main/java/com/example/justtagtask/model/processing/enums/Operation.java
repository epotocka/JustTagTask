package com.example.justtagtask.model.processing.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.BinaryOperator;

public enum Operation {

    ADD(BigDecimal::add),
    SUBTRACT(BigDecimal::subtract),
    MULTIPLY(BigDecimal::multiply),
    DIVIDE(Operation::divide);

    private static final int DIVISION_SCALE = 10;

    private final BinaryOperator<BigDecimal> function;

    Operation(BinaryOperator<BigDecimal> function) {
        this.function = function;
    }

    public BigDecimal apply(BigDecimal left, BigDecimal right) {
        return function.apply(left, right);
    }

    private static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return divisor.signum() == 0 ? null : dividend.divide(divisor, DIVISION_SCALE, RoundingMode.HALF_UP);
    }

}
