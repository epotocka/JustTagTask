package com.example.justtagtask.model.canonical;

import java.math.BigDecimal;
import java.util.UUID;

public record AdRecord(
        UUID fileId,
        long rowNumber,
        long advertiserId,
        long campaignId,
        long creativeId,
        UUID adBoxUuid,
        long impressions,
        long clicks,
        BigDecimal revenue,
        BigDecimal advertiserMargin) {
}
