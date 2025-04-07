package viettel.dac.prototype.execution.model;

import lombok.Data;

@Data
public class ExecutionSummary {
    private int totalIntents;
    private int completed;
    private int failed;
    private double averageDuration;
    private boolean hasErrors;
}

