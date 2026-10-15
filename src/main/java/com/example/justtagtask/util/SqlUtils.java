package com.example.justtagtask.util;

import com.example.justtagtask.model.processing.Aggregation;
import com.example.justtagtask.model.processing.ResultTable;
import com.example.justtagtask.model.processing.RowOperation;
import com.example.justtagtask.model.processing.RunDefinition;

public final class SqlUtils {

    public static final String AD_RECORD_TABLE = "canonical.ad_record";
    public static final String FILE_ID_PARAMETER = "fileId";
    public static final String FILE_ID_FILTER = "file_id = :" + FILE_ID_PARAMETER;
    public static final String ROW_NUMBER_COLUMN = "row_number";
    public static final String LEFT_OPERAND_COLUMN = "left_operand";
    public static final String RIGHT_OPERAND_COLUMN = "right_operand";

    public static final String INSERT_AD_RECORD = """
            INSERT INTO canonical.ad_record (
                file_id, row_number, advertiser_id, campaign_id, creative_id,
                ad_box_uuid, impressions, clicks, revenue, advertiser_margin)
            VALUES (
                :fileId, :rowNumber, :advertiserId, :campaignId, :creativeId,
                :adBoxUuid, :impressions, :clicks, :revenue, :advertiserMargin)
            ON CONFLICT (file_id, row_number) DO NOTHING
            """;

    private static final String OPERAND_COLUMNS = "row_number, %s AS left_operand, %s AS right_operand";

    private static final String CREATE_ROW_OPERATION_RESULT_TABLE = """
            CREATE TABLE %s (row_number BIGINT PRIMARY KEY, result NUMERIC)
            """;

    private static final String CREATE_AGGREGATION_RESULT_TABLE = """
            CREATE TABLE %s (group_value %s PRIMARY KEY, result NUMERIC)
            """;

    private static final String INSERT_ROW_RESULT = """
            INSERT INTO %s (row_number, result)
            VALUES (:rowNumber, :result)
            ON CONFLICT (row_number) DO NOTHING
            """;

    private static final String INSERT_AGGREGATION_RESULTS = """
            INSERT INTO %1$s (group_value, result)
            SELECT %2$s, %3$s(%4$s)
            FROM canonical.ad_record
            WHERE file_id = ?
            GROUP BY %2$s
            ON CONFLICT (group_value) DO NOTHING
            """;

    private SqlUtils() {
    }

    public static String operandColumns(RowOperation operation) {
        return OPERAND_COLUMNS.formatted(operation.leftColumn().columnName(), operation.rightColumn().columnName());
    }

    public static String createResultTable(ResultTable table, RunDefinition definition) {
        return switch (definition) {
            case RowOperation _ -> CREATE_ROW_OPERATION_RESULT_TABLE.formatted(table.name());
            case Aggregation aggregation -> CREATE_AGGREGATION_RESULT_TABLE.formatted(table.name(), aggregation.groupBy().sqlType());
        };
    }

    public static String insertRowResult(ResultTable table) {
        return INSERT_ROW_RESULT.formatted(table.name());
    }

    public static String insertAggregationResults(ResultTable table, Aggregation aggregation) {
        return INSERT_AGGREGATION_RESULTS.formatted(
                table.name(),
                aggregation.groupBy().columnName(),
                aggregation.function().name(),
                aggregation.valueColumn().columnName());
    }

}
