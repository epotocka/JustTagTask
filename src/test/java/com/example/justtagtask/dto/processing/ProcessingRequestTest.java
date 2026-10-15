package com.example.justtagtask.dto.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.justtagtask.exception.processing.InvalidProcessingRequestException;
import com.example.justtagtask.model.canonical.enums.AdColumn;
import com.example.justtagtask.model.processing.Aggregation;
import com.example.justtagtask.model.processing.RowOperation;
import com.example.justtagtask.model.processing.enums.AggregateFunction;
import com.example.justtagtask.model.processing.enums.Operation;
import org.junit.jupiter.api.Test;

class ProcessingRequestTest {

    private static final String MEASURES = "impressions, clicks, revenue, advertiserMargin";

    @Test
    void turnsAnOperationRequestIntoARowOperation() {
        ProcessingRequest request = new ProcessingRequest.OperationRequest("revenue", "advertiserMargin", Operation.MULTIPLY);

        assertThat(request.toDefinition())
                .isEqualTo(new RowOperation(AdColumn.REVENUE, AdColumn.ADVERTISER_MARGIN, Operation.MULTIPLY));
    }

    @Test
    void turnsAnAggregationRequestIntoAnAggregation() {
        ProcessingRequest request = new ProcessingRequest.AggregationRequest("adBoxUUID", "clicks", AggregateFunction.SUM);

        assertThat(request.toDefinition())
                .isEqualTo(new Aggregation(AdColumn.AD_BOX_UUID, AdColumn.CLICKS, AggregateFunction.SUM));
    }

    @Test
    void rejectsAnUnknownColumn() {
        ProcessingRequest request = new ProcessingRequest.OperationRequest("profit", "clicks", Operation.ADD);

        assertThatThrownBy(request::toDefinition)
                .isInstanceOf(InvalidProcessingRequestException.class)
                .hasMessageContaining("leftColumn must be one of: " + MEASURES);
    }

    @Test
    void rejectsANonNumericOperand() {
        ProcessingRequest request = new ProcessingRequest.OperationRequest("clicks", "campaignId", Operation.ADD);

        assertThatThrownBy(request::toDefinition)
                .isInstanceOf(InvalidProcessingRequestException.class)
                .hasMessageContaining("rightColumn must be one of: " + MEASURES);
    }

    @Test
    void rejectsANonNumericValueColumn() {
        ProcessingRequest request = new ProcessingRequest.AggregationRequest("campaignId", "adBoxUUID", AggregateFunction.MAX);

        assertThatThrownBy(request::toDefinition)
                .isInstanceOf(InvalidProcessingRequestException.class)
                .hasMessageContaining("valueColumn must be one of: " + MEASURES);
    }

    @Test
    void rejectsMissingSettings() {
        assertThatThrownBy(new ProcessingRequest.OperationRequest("clicks", "impressions", null)::toDefinition)
                .isInstanceOf(InvalidProcessingRequestException.class)
                .hasMessageContaining("operation is required");
        assertThatThrownBy(new ProcessingRequest.AggregationRequest(null, "clicks", AggregateFunction.SUM)::toDefinition)
                .isInstanceOf(InvalidProcessingRequestException.class)
                .hasMessageContaining("groupBy must be one of");
    }

}
