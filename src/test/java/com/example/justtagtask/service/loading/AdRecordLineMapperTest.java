package com.example.justtagtask.service.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.justtagtask.model.canonical.AdRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.infrastructure.item.file.transform.IncorrectTokenCountException;

class AdRecordLineMapperTest {

    private static final UUID FILE_ID = UUID.randomUUID();

    private final AdRecordLineMapper mapper = new AdRecordLineMapper(FILE_ID);

    @Test
    void mapsAValidLineAndNumbersRowsAfterTheHeader() {
        AdRecord record = mapper.mapLine("5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927", 2);

        assertThat(record).isEqualTo(new AdRecord(
                FILE_ID, 1, 5342, 11380, 526779, UUID.fromString("287d06ca-6f4c-469a-8b22-d3081c8eaee9"),
                591, 22, new BigDecimal("2.9798"), new BigDecimal("0.1927")));
    }

    @Test
    void acceptsWhitespaceAroundValues() {
        AdRecord record = mapper.mapLine(" 5342 ; 11380;526779; 287d06ca-6f4c-469a-8b22-d3081c8eaee9 ;591;22 ; 2.9798;0.1927 ", 2);

        assertThat(record.advertiserId()).isEqualTo(5342);
        assertThat(record.adBoxUuid()).isEqualTo(UUID.fromString("287d06ca-6f4c-469a-8b22-d3081c8eaee9"));
        assertThat(record.advertiserMargin()).isEqualByComparingTo("0.1927");
    }

    @Test
    void acceptsTrailingZerosBeyondFourDecimals() {
        AdRecord record = mapper.mapLine("5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.979800;0.1927", 2);

        assertThat(record.revenue()).isEqualByComparingTo("2.9798");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            ";;;;;;;",
            ";;;;;;;;;;",
            "5342;;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927",
            "5342;   ;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927",
            "5342;null;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927",
            "5342;NULL;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927",
            "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798",
            "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927;extra",
            "\"5342;1\";11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927",
            "5342;11380;526779;not-a-uuid;591;22;2.9798;0.1927",
            "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;many;2.9798;0.1927",
            "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.97981;0.1927",
            "5342;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;1234567890123456.0;0.1927",
            "99999999999999999999;11380;526779;287d06ca-6f4c-469a-8b22-d3081c8eaee9;591;22;2.9798;0.1927"
    })
    void rejectsMalformedLines(String line) {
        assertThatThrownBy(() -> mapper.mapLine(line, 2))
                .isInstanceOfAny(IllegalArgumentException.class, IncorrectTokenCountException.class);
    }

}
