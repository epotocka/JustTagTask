package com.example.justtagtask.model.processing.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class OperationTest {

    private static final BigDecimal REVENUE = new BigDecimal("2.9798");
    private static final BigDecimal MARGIN = new BigDecimal("0.1927");

    @Test
    void adds() {
        assertThat(Operation.ADD.apply(REVENUE, MARGIN)).isEqualByComparingTo("3.1725");
    }

    @Test
    void subtracts() {
        assertThat(Operation.SUBTRACT.apply(REVENUE, MARGIN)).isEqualByComparingTo("2.7871");
    }

    @Test
    void multipliesWithoutRounding() {
        assertThat(Operation.MULTIPLY.apply(REVENUE, MARGIN)).isEqualByComparingTo("0.57420746");
    }

    @Test
    void dividesToTenDecimalPlaces() {
        assertThat(Operation.DIVIDE.apply(new BigDecimal("22"), new BigDecimal("591"))).isEqualByComparingTo("0.0372250423");
    }

    @Test
    void dividingByZeroGivesNoResult() {
        assertThat(Operation.DIVIDE.apply(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
        assertThat(Operation.DIVIDE.apply(BigDecimal.ONE, new BigDecimal("0.0000"))).isNull();
    }

}
