package viettel.dac.prototype.tool.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.URL;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import viettel.dac.prototype.tool.enums.HttpMethodType;

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

    @NotBlank(message = "Tool name is required")
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @NotBlank(message = "Description is required")
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @NotBlank(message = "Endpoint is required")
    @URL(message = "Endpoint must be a valid URL")
    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "authentication_type")
    private String authenticationType;

    @NotNull(message = "HTTP method is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false)
    private HttpMethodType httpMethod;

    @Positive(message = "Timeout must be a positive value")
    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 5000;

    @OneToMany(mappedBy = "tool", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
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