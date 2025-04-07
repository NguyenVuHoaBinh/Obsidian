package viettel.dac.prototype.execution.model;

import lombok.Data;
import viettel.dac.prototype.execution.enums.ExecutionState;

import java.util.Map;

@Data
public class Intent {
    private String intent;
    private double confidence;
    private Map<String, Object> parameters;
    private ExecutionState state = ExecutionState.PENDING;
}
