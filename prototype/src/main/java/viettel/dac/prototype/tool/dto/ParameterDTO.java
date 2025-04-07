package viettel.dac.prototype.tool.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import viettel.dac.prototype.tool.model.Parameter;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParameterDTO {
    @NotBlank
    private String name;

    @NotBlank
    private String type;

    private boolean required = false;

    private String description;

    private String defaultValue;

    // Convert Entity to DTO
    public static ParameterDTO fromEntity(Parameter parameter) {
        return new ParameterDTO(
                parameter.getName(),
                parameter.getType(),
                parameter.isRequired(),
                parameter.getDescription(),
                parameter.getDefaultValue()
        );
    }
}
