package com.ledger.api.dto;

import java.util.List;

public record InstrumentImportResponse(
        InstrumentResponse instrument,
        List<InstrumentPaymentResponse> payments
) {
}
