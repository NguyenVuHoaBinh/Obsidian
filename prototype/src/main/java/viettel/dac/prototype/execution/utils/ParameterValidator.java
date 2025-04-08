package viettel.dac.prototype.execution.utils;


import viettel.dac.prototype.execution.exception.MissingParameterException;
import viettel.dac.prototype.tool.model.Parameter;

import java.util.List;
import java.util.Map;

public class ParameterValidator {

    /**
     * Validates that all required parameters are present for a tool's execution.
     *
     * @param parameters       The list of parameters defined by the tool.
     * @param providedParams   The parameters provided during execution.
     */
    public static void validateParameters(List<Parameter> parameters, Map<String, Object> providedParams) {
        for (Parameter param : parameters) {
            if (param.isRequired() && !providedParams.containsKey(param.getName())) {
                throw new MissingParameterException("Missing required parameter: " + param.getName());
            }
        }
    }
}

