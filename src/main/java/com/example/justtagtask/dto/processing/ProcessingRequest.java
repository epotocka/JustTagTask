package com.example.justtagtask.dto.processing;

import java.util.List;
import java.util.stream.Collectors;

import com.example.justtagtask.exception.processing.InvalidProcessingRequestException;
import com.example.justtagtask.model.canonical.enums.AdColumn;
import com.example.justtagtask.model.processing.Aggregation;
import com.example.justtagtask.model.processing.RowOperation;
import com.example.justtagtask.model.processing.RunDefinition;
import com.example.justtagtask.model.processing.enums.AggregateFunction;
import com.example.justtagtask.model.processing.enums.Operation;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProcessingRequest.OperationRequest.class, name = "OPERATION"),
        @JsonSubTypes.Type(value = ProcessingRequest.AggregationRequest.class, name = "AGGREGATION")
})
public sealed interface ProcessingRequest {

    RunDefinition toDefinition();

    record OperationRequest(String leftColumn, String rightColumn, Operation operation) implements ProcessingRequest {

        @Override
        public RunDefinition toDefinition() {
            return new RowOperation(
                    column("leftColumn", leftColumn, AdColumn.measures()),
                    column("rightColumn", rightColumn, AdColumn.measures()),
                    required("operation", operation));
        }

    }

    record AggregationRequest(String groupBy, String valueColumn, AggregateFunction function) implements ProcessingRequest {

        @Override
        public RunDefinition toDefinition() {
            return new Aggregation(
                    column("groupBy", groupBy, List.of(AdColumn.values())),
                    column("valueColumn", valueColumn, AdColumn.measures()),
                    required("function", function));
        }

    }

    private static AdColumn column(String field, String header, List<AdColumn> allowed) {
        return AdColumn.fromHeader(header)
                .filter(allowed::contains)
                .orElseThrow(() -> new InvalidProcessingRequestException(field + " must be one of: " + headers(allowed)));
    }

    private static <T> T required(String field, T value) {
        if (value == null) {
            throw new InvalidProcessingRequestException(field + " is required");
        }
        return value;
    }

    private static String headers(List<AdColumn> columns) {
        return columns.stream().map(AdColumn::header).collect(Collectors.joining(", "));
    }

}
