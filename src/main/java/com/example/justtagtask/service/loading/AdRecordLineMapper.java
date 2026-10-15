package com.example.justtagtask.service.loading;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import com.example.justtagtask.model.canonical.AdRecord;
import com.example.justtagtask.model.canonical.enums.AdColumn;
import org.springframework.batch.infrastructure.item.file.LineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;

public final class AdRecordLineMapper implements LineMapper<AdRecord> {

    private static final int HEADER_LINES = 1;
    private static final String NULL_LITERAL = "null";
    private static final int MAX_DECIMAL_SCALE = 4;
    private static final int MAX_DECIMAL_INTEGER_DIGITS = 15;

    private final UUID fileId;
    private final DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer(AdColumn.CSV_DELIMITER);

    public AdRecordLineMapper(UUID fileId) {
        this.fileId = fileId;
        this.tokenizer.setNames(Arrays.stream(AdColumn.values()).map(AdColumn::header).toArray(String[]::new));
    }

    @Override
    public AdRecord mapLine(String line, int lineNumber) {
        FieldSet fields = tokenizer.tokenize(line);
        return new AdRecord(
                fileId,
                lineNumber - HEADER_LINES,
                wholeNumber(fields, AdColumn.ADVERTISER_ID),
                wholeNumber(fields, AdColumn.CAMPAIGN_ID),
                wholeNumber(fields, AdColumn.CREATIVE_ID),
                UUID.fromString(value(fields, AdColumn.AD_BOX_UUID)),
                wholeNumber(fields, AdColumn.IMPRESSIONS),
                wholeNumber(fields, AdColumn.CLICKS),
                decimal(fields, AdColumn.REVENUE),
                decimal(fields, AdColumn.ADVERTISER_MARGIN));
    }

    private static String value(FieldSet fields, AdColumn column) {
        String value = fields.readString(column.header());
        if (value == null || value.isBlank() || NULL_LITERAL.equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(column.header() + " has no value");
        }
        return value;
    }

    private static long wholeNumber(FieldSet fields, AdColumn column) {
        return Long.parseLong(value(fields, column));
    }

    private static BigDecimal decimal(FieldSet fields, AdColumn column) {
        BigDecimal decimal = new BigDecimal(value(fields, column));
        BigDecimal significant = decimal.stripTrailingZeros();
        if (significant.scale() > MAX_DECIMAL_SCALE || significant.precision() - significant.scale() > MAX_DECIMAL_INTEGER_DIGITS) {
            throw new IllegalArgumentException(column.header() + " doesn't fit " + column.sqlType() + ": " + decimal.toPlainString());
        }
        return decimal;
    }

}
