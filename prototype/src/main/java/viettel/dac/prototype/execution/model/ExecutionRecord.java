package viettel.dac.prototype.execution.model;

import lombok.Data;
import viettel.dac.prototype.execution.enums.ExecutionStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ExecutionRecord {
    private String intent;
    private ExecutionStatus status;
    private Map<String, Object> parameters;
    private Map<String, Object> result;
    private String error;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMillis;
}
