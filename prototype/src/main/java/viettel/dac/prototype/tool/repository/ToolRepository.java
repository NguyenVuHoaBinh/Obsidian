package viettel.dac.prototype.tool.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import viettel.dac.prototype.tool.model.Tool;

import java.util.List;
import java.util.Optional;

@Repository
public interface ToolRepository extends JpaRepository<Tool, Long> {

    // Find a tool by its unique name
    Optional<Tool> findByName(String name);

    // Check if a tool exists by name
    boolean existsByName(String name);

    // Fetch a tool with its parameters (eager loading)
    @Query("SELECT t FROM Tool t LEFT JOIN FETCH t.parameters WHERE t.id = :id")
    Optional<Tool> findByIdWithParameters(Long id);

    // Fetch all tools with their parameters (eager loading)
    @Query("SELECT DISTINCT t FROM Tool t LEFT JOIN FETCH t.parameters")
    List<Tool> findAllWithParameters();

    // Find tools by HTTP method
    List<Tool> findByHttpMethod(String httpMethod);
}

