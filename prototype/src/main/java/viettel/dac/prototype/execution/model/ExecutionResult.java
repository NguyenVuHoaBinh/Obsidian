package viettel.dac.prototype.execution.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExecutionResult {
    private String analysisId;
    private List<ExecutionRecord> executionRecords = new ArrayList<>();
    private LocalDateTime executionTime = LocalDateTime.now();
    private ExecutionSummary summary;
}

