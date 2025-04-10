package viettel.dac.prototype.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Filter to capture execution time of API requests and add timing information to response headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ExecutionTimeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Start timing the request
        long startTime = System.currentTimeMillis();

        // Wrap the response to prevent response being committed before we can add headers
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            // Continue with the filter chain
            filterChain.doFilter(request, wrappedResponse);
        } finally {
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;

            // Add execution time to response header
            wrappedResponse.setHeader("X-Execution-Time", String.valueOf(executionTime));

            // Add additional execution info if it exists in request attributes
            Object executedTools = request.getAttribute("X-Executed-Tools");
            if (executedTools != null) {
                wrappedResponse.setHeader("X-Executed-Tools", executedTools.toString());
            }

            Object detailedTimings = request.getAttribute("X-Detailed-Timings");
            if (detailedTimings != null) {
                wrappedResponse.setHeader("X-Detailed-Timings", detailedTimings.toString());
            }

            // Log the execution time
            log.debug("Request to {} completed in {}ms", request.getRequestURI(), executionTime);

            // Ensure the response is properly committed
            wrappedResponse.copyBodyToResponse();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only apply this filter to API requests
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }
}