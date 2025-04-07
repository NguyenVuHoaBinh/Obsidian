package viettel.dac.prototype.tool.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tool")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @NotBlank
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @NotBlank
    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "authentication_type")
    private String authenticationType;

    @NotNull
    @Column(name = "http_method", nullable = false)
    private String httpMethod;

    @Positive
    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 5000;

    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Parameter> parameters = new ArrayList<>();

    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Dependency> dependencies = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper methods for managing relationships
    public void addParameter(Parameter parameter) {
        parameters.add(parameter);
        parameter.setTool(this);
    }

    public void addDependency(Tool dependentTool) {
        Dependency dependency = new Dependency();
        dependency.setTool(this);
        dependency.setDependsOn(dependentTool);
        dependencies.add(dependency);
    }
}
