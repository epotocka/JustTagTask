package com.example.justtagtask.model.processing;

import java.math.BigDecimal;

public record OperandRow(long rowNumber, BigDecimal left, BigDecimal right) {
}
