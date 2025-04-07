package viettel.dac.prototype.execution.utils;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import viettel.dac.prototype.execution.model.Intent;
import viettel.dac.prototype.tool.exception.CircularDependencyException;
import viettel.dac.prototype.tool.model.Tool;
import viettel.dac.prototype.tool.repository.ToolRepository;

import java.util.*;

@Component
public class DependencyResolver {

    @Autowired
    private ToolRepository toolRepository;

    /**
     * Resolves the correct execution order of intents based on tool dependencies.
     *
     * @param intents List of intents to execute.
     * @return Ordered list of intents based on dependencies.
     */
    public List<Intent> resolveExecutionOrder(List<Intent> intents) {
        // Build a dependency graph
        Map<String, List<String>> dependencyGraph = buildDependencyGraph(intents);

        // Perform topological sorting
        return topologicalSort(intents, dependencyGraph);
    }

    /**
     * Builds a dependency graph for the given intents.
     *
     * @param intents The list of intents to analyze.
     * @return A dependency graph where each tool maps to its dependencies.
     */
    private Map<String, List<String>> buildDependencyGraph(List<Intent> intents) {
        Map<String, List<String>> graph = new HashMap<>();

        for (Intent intent : intents) {
            Tool tool = toolRepository.findByName(intent.getIntent())
                    .orElseThrow(() -> new RuntimeException("Tool not found: " + intent.getIntent()));

            graph.put(tool.getName(), tool.getDependencies());
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
    private List<Intent> topologicalSort(List<Intent> intents, Map<String, List<String>> dependencyGraph) {
        // Initialize in-degree map
        Map<String, Integer> inDegree = new HashMap<>();
        for (String tool : dependencyGraph.keySet()) {
            inDegree.put(tool, 0);
        }
        for (List<String> dependencies : dependencyGraph.values()) {
            for (String dep : dependencies) {
                inDegree.put(dep, inDegree.getOrDefault(dep, 0) + 1);
            }
        }

        // Initialize queue with tools having no dependencies
        Queue<String> queue = new LinkedList<>();
        for (String tool : inDegree.keySet()) {
            if (inDegree.get(tool) == 0) {
                queue.add(tool);
            }
        }

        // Perform topological sort
        List<String> sortedTools = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedTools.add(current);

            for (String dep : dependencyGraph.getOrDefault(current, Collections.emptyList())) {
                inDegree.put(dep, inDegree.get(dep) - 1);
                if (inDegree.get(dep) == 0) {
                    queue.add(dep);
                }
            }
        }

        // Check for circular dependencies
        if (sortedTools.size() != dependencyGraph.size()) {
            throw new CircularDependencyException("Circular dependency detected!");
        }

        // Map sorted tools back to intents
        Map<String, Intent> intentMap = new HashMap<>();
        for (Intent intent : intents) {
            intentMap.put(intent.getIntent(), intent);
        }

        List<Intent> orderedIntents = new ArrayList<>();
        for (String toolName : sortedTools) {
            orderedIntents.add(intentMap.get(toolName));
        }

        return orderedIntents;
    }
}
