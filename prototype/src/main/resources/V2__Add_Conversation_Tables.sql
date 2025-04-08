
CREATE TABLE IF NOT EXISTS conversation (
                                            id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    metadata JSON,
    INDEX idx_conversation_user_id (user_id),
    INDEX idx_conversation_updated_at (updated_at)
    );

-- Message Table: Stores individual messages in conversations
CREATE TABLE IF NOT EXISTS message (
                                       id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    type VARCHAR(20) NOT NULL, -- 'USER', 'SYSTEM'
    content TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata JSON,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    INDEX idx_message_conversation_id (conversation_id),
    INDEX idx_message_timestamp (timestamp)
    );

-- Conversation Execution Record: Links conversations with execution records
CREATE TABLE IF NOT EXISTS conversation_execution (
                                                      id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    execution_id VARCHAR(36) NOT NULL,
    message_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    FOREIGN KEY (message_id) REFERENCES message(id) ON DELETE SET NULL,
    INDEX idx_conversation_execution_conversation_id (conversation_id),
    INDEX idx_conversation_execution_execution_id (execution_id)
    );

-- Session Context: Stores session context for enhanced conversations
CREATE TABLE IF NOT EXISTS session_context (
                                               id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    context_key VARCHAR(255) NOT NULL,
    context_value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE,
    UNIQUE INDEX idx_session_context_unique (conversation_id, context_key),
    INDEX idx_session_context_key (context_key)
    );
    );