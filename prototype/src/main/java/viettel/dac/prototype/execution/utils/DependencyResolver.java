package viettel.dac.prototype.execution.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import viettel.dac.prototype.execution.exception.CircularDependencyException;
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
     * @throws CircularDependencyException if a circular dependency is detected.
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
     * @throws CircularDependencyException if a circular dependency is detected.
     */
    /**
     * Performs topological sorting on the dependency graph to determine execution order.
     *
     * @param intents           The list of intents to sort.
     * @param dependencyGraph   The dependency graph mapping tools to their dependencies.
     * @return Ordered list of intents based on dependencies.
     * @throws CircularDependencyException if a circular dependency is detected.
     */
    private List<Intent> topologicalSort(List<Intent> intents, Map<String, Set<String>> dependencyGraph) {
        // Create a map for intent lookup by name and unique identifier
        Map<String, Intent> intentLookup = new HashMap<>();
        Map<String, String> intentToUniqueId = new HashMap<>();

        // Assign a unique ID to each intent and build the lookup maps
        for (int i = 0; i < intents.size(); i++) {
            Intent intent = intents.get(i);
            String uniqueId = intent.getIntent() + "_" + i; // Use index as a suffix to create unique IDs
            intentLookup.put(uniqueId, intent);
            intentToUniqueId.put(uniqueId, intent.getIntent()); // Maps back to original intent name
        }

        // Build modified dependency graph with unique IDs
        Map<String, Set<String>> uniqueIdGraph = new HashMap<>();
        for (String uniqueId : intentLookup.keySet()) {
            String intentName = intentToUniqueId.get(uniqueId);
            Set<String> dependencies = dependencyGraph.getOrDefault(intentName, Collections.emptySet());

            // For each dependency name, find all intents with that name
            Set<String> uniqueDependencies = new HashSet<>();
            for (String dependency : dependencies) {
                // Find all unique IDs that map to this dependency name
                for (Map.Entry<String, String> entry : intentToUniqueId.entrySet()) {
                    if (entry.getValue().equals(dependency)) {
                        uniqueDependencies.add(entry.getKey());
                    }
                }
            }

            uniqueIdGraph.put(uniqueId, uniqueDependencies);
        }

        // Calculate in-degree for each node (number of dependencies)
        Map<String, Integer> inDegree = new HashMap<>();

        // Initialize all nodes with in-degree 0
        for (String node : uniqueIdGraph.keySet()) {
            inDegree.put(node, 0);
        }

        // Calculate in-degrees
        for (Set<String> dependencies : uniqueIdGraph.values()) {
            for (String dependency : dependencies) {
                inDegree.put(dependency, inDegree.getOrDefault(dependency, 0) + 1);
            }
        }

        // Queue of nodes with no dependencies (in-degree of 0)
        Queue<String> queue = new LinkedList<>();
        for (String node : inDegree.keySet()) {
            if (inDegree.get(node) == 0) {
                queue.add(node);
                log.trace("Added to initial queue: {} (no dependencies)", node);
            }
        }

        List<String> sortedUniqueIds = new ArrayList<>();

        // Process the queue
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedUniqueIds.add(current);
            log.trace("Processing node: {}", current);

            // For each node that depends on the current node
            for (Map.Entry<String, Set<String>> entry : uniqueIdGraph.entrySet()) {
                String dependent = entry.getKey();
                Set<String> dependencies = entry.getValue();

                if (dependencies.contains(current)) {
                    // Reduce its in-degree
                    inDegree.put(dependent, inDegree.get(dependent) - 1);
                    log.trace("Reducing in-degree of {} to {}", dependent, inDegree.get(dependent));

                    // If it has no more dependencies, add it to the queue
                    if (inDegree.get(dependent) == 0) {
                        queue.add(dependent);
                        log.trace("Added to queue: {} (no remaining dependencies)", dependent);
                    }
                }
            }
        }

        // Check for circular dependencies
        if (sortedUniqueIds.size() != uniqueIdGraph.size()) {
            log.error("Circular dependency detected! Processed {} of {} nodes",
                    sortedUniqueIds.size(), uniqueIdGraph.size());

            Set<String> unprocessedNodes = new HashSet<>(uniqueIdGraph.keySet());
            unprocessedNodes.removeAll(sortedUniqueIds);
            log.error("Unprocessed nodes involved in circular dependencies: {}",
                    String.join(", ", unprocessedNodes));

            String circularPath = findCircularPath(uniqueIdGraph, unprocessedNodes);
            throw new CircularDependencyException("Circular dependency detected: " + circularPath);
        }

        // Map sorted unique IDs back to intents
        List<Intent> orderedIntents = sortedUniqueIds.stream()
                .map(intentLookup::get)
                .collect(Collectors.toList());

        return orderedIntents;
    }

    /**
     * Builds a reverse dependency graph where edges go from dependencies to dependents.
     *
     * @param graph The original dependency graph
     * @return The reverse dependency graph
     */
    private Map<String, Set<String>> buildReverseGraph(Map<String, Set<String>> graph) {
        Map<String, Set<String>> reverseGraph = new HashMap<>();

        // Initialize all nodes
        for (String node : graph.keySet()) {
            reverseGraph.put(node, new HashSet<>());
        }

        // Add reverse edges
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            String dependent = entry.getKey();
            for (String dependency : entry.getValue()) {
                reverseGraph.get(dependency).add(dependent);
            }
        }

        return reverseGraph;
    }

    /**
     * Calculates the in-degree (number of dependencies) for each node in the graph.
     *
     * @param graph The dependency graph
     * @return A map of node names to their in-degrees
     */
    private Map<String, Integer> calculateInDegrees(Map<String, Set<String>> graph) {
        Map<String, Integer> inDegree = new HashMap<>();

        // Initialize all nodes with in-degree 0
        for (String node : graph.keySet()) {
            inDegree.put(node, 0);
        }

        // Calculate in-degrees
        for (Set<String> dependencies : graph.values()) {
            for (String dependency : dependencies) {
                inDegree.put(dependency, inDegree.get(dependency) + 1);
            }
        }

        return inDegree;
    }

    /**
     * Finds and returns a circular path in the dependency graph for diagnostic purposes.
     *
     * @param graph The dependency graph
     * @param startNodes A set of nodes to start the search from
     * @return A string representation of a circular path in the graph
     */
    private String findCircularPath(Map<String, Set<String>> graph, Set<String> startNodes) {
        if (startNodes.isEmpty()) {
            return "Unknown circular dependency - no start nodes provided";
        }

        // Start with any node involved in a circular dependency
        String startNode = startNodes.iterator().next();

        // Track visited nodes and the current recursion path
        Set<String> visited = new HashSet<>();
        List<String> currentPath = new ArrayList<>();
        Map<String, Boolean> onStack = new HashMap<>();

        // Initialize for all nodes
        for (String node : graph.keySet()) {
            onStack.put(node, false);
        }

        // Find the cycle
        detectCycle(graph, startNode, visited, currentPath, onStack);

        // If we found a cycle, return its representation
        if (!currentPath.isEmpty()) {
            return String.join(" -> ", currentPath);
        }

        // Fallback - check all unvisited nodes if we didn't find a cycle yet
        for (String node : startNodes) {
            if (!visited.contains(node)) {
                detectCycle(graph, node, visited, currentPath, onStack);
                if (!currentPath.isEmpty()) {
                    return String.join(" -> ", currentPath);
                }
            }
        }

        // Last resort - try to at least report the nodes involved
        if (!startNodes.isEmpty()) {
            return "Circular dependency involving: " + String.join(", ", startNodes);
        }

        return "Unknown circular dependency";
    }

    /**
     * Detects a cycle in the graph using DFS and updates the currentPath if found.
     *
     * @param graph The dependency graph
     * @param node The current node being visited
     * @param visited Set of all visited nodes
     * @param currentPath Will be updated with the cycle path if found
     * @param onStack Marks if a node is currently on the recursion stack
     * @return true if a cycle is found, false otherwise
     */
    private boolean detectCycle(Map<String, Set<String>> graph, String node, Set<String> visited,
                                List<String> currentPath, Map<String, Boolean> onStack) {
        visited.add(node);
        onStack.put(node, true);

        // Visit all dependencies
        for (String dependency : graph.getOrDefault(node, Collections.emptySet())) {
            // Skip if not in graph (could happen in unique ID cases)
            if (!graph.containsKey(dependency)) {
                continue;
            }

            // If not visited yet, recursively visit
            if (!visited.contains(dependency)) {
                if (detectCycle(graph, dependency, visited, currentPath, onStack)) {
                    // Add to path only if it's not already added (to avoid duplicates)
                    if (currentPath.isEmpty() || !currentPath.get(0).equals(dependency)) {
                        currentPath.add(0, node);
                    }
                    return true;
                }
            }
            // If already on stack, we found a cycle
            else if (onStack.get(dependency)) {
                // Reconstruct cycle path
                currentPath.clear();
                currentPath.add(dependency); // Start with the node where cycle detected
                currentPath.add(node);       // Add current node
                return true;
            }
        }

        // Remove from recursion stack when backtracking
        onStack.put(node, false);
        return false;
    }

    /**
     * Depth-first search to find a cycle in the graph.
     *
     * @param graph The dependency graph
     * @param current The current node being visited
     * @param visited Set of all visited nodes
     * @param recursionStack Set of nodes in the current recursion stack
     * @param predecessor Map to track predecessors for cycle reconstruction
     * @return true if a cycle is found, false otherwise
     */
    private boolean findCycle(Map<String, Set<String>> graph, String current,
                              Set<String> visited, Set<String> recursionStack,
                              Map<String, String> predecessor) {
        // Add current node to visited and recursion stack
        visited.add(current);
        recursionStack.add(current);

        // Visit all dependencies
        for (String dependency : graph.getOrDefault(current, Collections.emptySet())) {
            // If not visited, recursively visit
            if (!visited.contains(dependency)) {
                predecessor.put(dependency, current);
                if (findCycle(graph, dependency, visited, recursionStack, predecessor)) {
                    return true;
                }
            }
            // If already in recursion stack, we found a cycle
            else if (recursionStack.contains(dependency)) {
                predecessor.put(dependency, current);
                return true;
            }
        }

        // Remove from recursion stack when backtracking
        recursionStack.remove(current);
        return false;
    }
}