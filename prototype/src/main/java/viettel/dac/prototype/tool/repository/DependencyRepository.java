package viettel.dac.prototype.tool.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import viettel.dac.prototype.tool.model.Dependency;
import viettel.dac.prototype.tool.model.Tool;

import java.util.List;

@Repository
public interface DependencyRepository extends JpaRepository<Dependency, Long> {

    // Find all dependencies for a specific tool
    List<Dependency> findByTool(Tool tool);

    // Find dependencies by tool name using JPQL
    @Query("SELECT d FROM Dependency d WHERE d.tool.name = :toolName")
    List<Dependency> findDependenciesByToolName(String toolName);

    // Check if a dependency exists between two tools
    boolean existsByToolAndDependsOn(Tool tool, Tool dependsOn);
}
