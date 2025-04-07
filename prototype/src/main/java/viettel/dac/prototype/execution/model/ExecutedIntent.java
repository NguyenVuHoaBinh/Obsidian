package viettel.dac.prototype.execution.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import viettel.dac.prototype.execution.enums.ExecutionStatus;

@Data
@AllArgsConstructor
public class ExecutedIntent {
    private String intent;
    private ExecutionStatus status;
    private Object result; // Partial results or response from the tool
    private String error;  // Error message if execution failed
}
