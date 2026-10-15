package com.example.justtagtask.model.canonical.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AdColumn {

    ADVERTISER_ID("advertiserId", "advertiser_id", "BIGINT", false),
    CAMPAIGN_ID("campaignId", "campaign_id", "BIGINT", false),
    CREATIVE_ID("creativeId", "creative_id", "BIGINT", false),
    AD_BOX_UUID("adBoxUUID", "ad_box_uuid", "UUID", false),
    IMPRESSIONS("impressions", "impressions", "BIGINT", true),
    CLICKS("clicks", "clicks", "BIGINT", true),
    REVENUE("revenue", "revenue", "NUMERIC(19, 4)", true),
    ADVERTISER_MARGIN("advertiserMargin", "advertiser_margin", "NUMERIC(19, 4)", true);

    public static final String CSV_DELIMITER = ";";

    private final String header;
    private final String columnName;
    private final String sqlType;
    private final boolean measure;

    AdColumn(String header, String columnName, String sqlType, boolean measure) {
        this.header = header;
        this.columnName = columnName;
        this.sqlType = sqlType;
        this.measure = measure;
    }

    public static Optional<AdColumn> fromHeader(String header) {
        return Arrays.stream(values()).filter(column -> column.header.equals(header)).findFirst();
    }

    public static List<AdColumn> measures() {
        return Arrays.stream(values()).filter(AdColumn::isMeasure).toList();
    }

    public static String csvHeader() {
        return Arrays.stream(values()).map(AdColumn::header).collect(Collectors.joining(CSV_DELIMITER));
    }

    @JsonValue
    public String header() {
        return header;
    }

    public String columnName() {
        return columnName;
    }

    public String sqlType() {
        return sqlType;
    }

    public boolean isMeasure() {
        return measure;
    }

}
