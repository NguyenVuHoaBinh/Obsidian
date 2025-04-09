package viettel.dac.prototype.execution.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import viettel.dac.prototype.execution.exception.ToolNotFoundException;
import viettel.dac.prototype.execution.model.Intent;
import viettel.dac.prototype.tool.model.Dependency;
import viettel.dac.prototype.tool.model.Tool;
import viettel.dac.prototype.tool.repository.DependencyRepository;
import viettel.dac.prototype.tool.repository.ToolRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility for resolving the execution order of intents based on tool dependencies.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DependencyResolver {

    private final ToolRepository toolRepository;
    private final DependencyRepository dependencyRepository;

    /**
     * Resolves the correct execution order of intents based on tool dependencies.
     * Uses caching to improve performance for repeated invocations with the same intents.
     *
     * @param intents List of intents to execute.
     * @return Ordered list of intents based on dependencies.
     * @throws ToolNotFoundException if a tool referenced by an intent is not found.
     */
    @Cacheable(value = "dependencyGraphs", keyGenerator = "intentListKeyGenerator")
    public List<Intent> resolveExecutionOrder(List<Intent> intents) {
        if (intents == null || intents.isEmpty()) {
            log.warn("No intents provided for dependency resolution");
            return Collections.emptyList();
        }

        log.debug("Resolving execution order for {} intents", intents.size());
        log.trace("Intent list: {}", intents.stream().map(Intent::getIntent).collect(Collectors.joining(", ")));

        // Build a dependency graph
        Map<String, Set<String>> dependencyGraph = buildDependencyGraph(intents);
        log.trace("Dependency graph built: {}", dependencyGraph);

        // Perform topological sorting
        List<Intent> result = topologicalSort(intents, dependencyGraph);
        log.debug("Resolved execution order: {}",
                result.stream().map(Intent::getIntent).collect(Collectors.joining(", ")));

        return result;
    }

    /**
     * Builds a dependency graph for the given intents.
     *
     * @param intents The list of intents to analyze.
     * @return A dependency graph where each tool maps to its direct dependencies.
     * @throws ToolNotFoundException if a tool referenced by an intent is not found.
     */
    private Map<String, Set<String>> buildDependencyGraph(List<Intent> intents) {
        Map<String, Set<String>> graph = new HashMap<>();
        Set<String> allToolNames = new HashSet<>();

        // Initialize graph with empty dependency sets for each intent
        for (Intent intent : intents) {
            String toolName = intent.getIntent();
            graph.put(toolName, new HashSet<>());
            allToolNames.add(toolName);
        }

        // Populate dependencies for each tool
        for (String toolName : allToolNames) {
            Tool tool = toolRepository.findByName(toolName)
                    .orElseThrow(() -> {
                        log.error("Tool not found: {}", toolName);
                        return new ToolNotFoundException(toolName);
                    });

            List<Dependency> dependencies = dependencyRepository.findByTool(tool);

            for (Dependency dependency : dependencies) {
                String dependencyName = dependency.getDependsOn().getName();

                // Only include dependencies that are in the current intent list
                if (allToolNames.contains(dependencyName)) {
                    graph.get(toolName).add(dependencyName);
                    log.trace("Added dependency: {} -> {}", toolName, dependencyName);
                }
            }
        }

        return graph;
    }

    /**
     * Performs topological sorting on the dependency graph to determine execution order.
     *
     * @param intents           The list of intents to sort.
     * @param dependencyGraph   The dependency graph mapping tools to their dependencies.
     * @return Ordered list of intents based on dependencies.
     */
    private List<Intent> topologicalSort(List<Intent> intents, Map<String, Set<String>> dependencyGraph) {
        // Create a map for intent lookup by name
        Map<String, Intent> intentMap = intents.stream()
                .collect(Collectors.toMap(Intent::getIntent, intent -> intent));

        // Calculate in-degree for each node (number of dependencies)
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : dependencyGraph.keySet()) {
            inDegree.put(node, 0);
        }
        for (Set<String> dependencies : dependencyGraph.values()) {
            for (String dependency : dependencies) {
                inDegree.put(dependency, inDegree.getOrDefault(dependency, 0) + 1);
            }
        }

        // Queue of nodes with no dependencies (in-degree of 0)
        Queue<String> queue = new LinkedList<>();
        for (String node : inDegree.keySet()) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
            }
        }

        List<String> sortedNodes = new ArrayList<>();

        // Process the queue
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedNodes.add(current);

            // For each node that depends on the current node
            for (String dependent : dependencyGraph.keySet()) {
                Set<String> dependencies = dependencyGraph.get(dependent);
                if (dependencies.contains(current)) {
                    // Reduce its in-degree
                    inDegree.put(dependent, inDegree.get(dependent) - 1);

                    // If it has no more dependencies, add it to the queue
                    if (inDegree.get(dependent) == 0) {
                        queue.add(dependent);
                    }
                }
            }
        }

        // Map sorted nodes back to intents
        return sortedNodes.stream()
                .map(intentMap::get)
                .collect(Collectors.toList());
    }
}