package viettel.dac.prototype.execution.model;

import lombok.Data;
import java.util.Map;
import java.util.Stack;

@Data
public class ToolExecutionContext {
    private Stack<String> executionStack = new Stack<>();
    private Map<String, Object> sharedContext;
    private int retryCount = 0;
    private String currentIntent;
}

