CREATE TABLE IF NOT EXISTS tool (
                                    id INT PRIMARY KEY AUTO_INCREMENT,
                                    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    authentication_type VARCHAR(50),
    http_method VARCHAR(10) NOT NULL,
    timeout_ms INT NOT NULL DEFAULT 5000,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tool_name (name)
    );

CREATE TABLE IF NOT EXISTS parameter (
                                         id INT PRIMARY KEY AUTO_INCREMENT,
                                         tool_id INT NOT NULL,
                                         name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT false,
    description TEXT,
    default_value VARCHAR(255),
    FOREIGN KEY (tool_id) REFERENCES tool(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_param_unique (tool_id, name)
    );

CREATE TABLE IF NOT EXISTS dependency (
                                          id INT PRIMARY KEY AUTO_INCREMENT,
                                          tool_id INT NOT NULL,
                                          depends_on_id INT NOT NULL,
                                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                          FOREIGN KEY (tool_id) REFERENCES tool(id) ON DELETE CASCADE,
    FOREIGN KEY (depends_on_id) REFERENCES tool(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_dependency_unique (tool_id, depends_on_id)
    );

CREATE TABLE execution_context (
                                   id VARCHAR(36) PRIMARY KEY,
                                   analysis_id VARCHAR(36) NOT NULL,
                                   execution_stack JSON NOT NULL,
                                   shared_context JSON,
                                   retry_count INT DEFAULT 0,
                                   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE execution_record (
                                  id VARCHAR(36) PRIMARY KEY,
                                  analysis_id VARCHAR(36) NOT NULL,
                                  intent VARCHAR(255) NOT NULL,
                                  status VARCHAR(20) NOT NULL,
                                  parameters JSON,
                                  result JSON,
                                  error TEXT,
                                  start_time TIMESTAMP,
                                  end_time TIMESTAMP,
                                  duration BIGINT
);
