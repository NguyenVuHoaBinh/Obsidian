// Simple icons for Lucide replacement
const Icons = {
    MessageCircle: (props) => (
        <svg xmlns="http://www.w3.org/2000/svg" width={props.size || 24} height={props.size || 24} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={props.className}>
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
        </svg>
    ),
    Send: (props) => (
        <svg xmlns="http://www.w3.org/2000/svg" width={props.size || 24} height={props.size || 24} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={props.className}>
            <line x1="22" y1="2" x2="11" y2="13"></line>
            <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
        </svg>
    ),
    AlertCircle: (props) => (
        <svg xmlns="http://www.w3.org/2000/svg" width={props.size || 24} height={props.size || 24} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={props.className}>
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
        </svg>
    ),
    PlusCircle: (props) => (
        <svg xmlns="http://www.w3.org/2000/svg" width={props.size || 24} height={props.size || 24} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={props.className}>
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="16"></line>
            <line x1="8" y1="12" x2="16" y2="12"></line>
        </svg>
    ),
    Clock: (props) => (
        <svg xmlns="http://www.w3.org/2000/svg" width={props.size || 24} height={props.size || 24} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={props.className}>
            <circle cx="12" cy="12" r="10"></circle>
            <polyline points="12 6 12 12 16 14"></polyline>
        </svg>
    ),
    Activity: (props) => (
        <svg xmlns="http://www.w3.org/2000/svg" width={props.size || 24} height={props.size || 24} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={props.className}>
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline>
        </svg>
    )
};

// Chat Interface Component with Operation Logging
function Chatinterface() {
    const [messages, setMessages] = React.useState([]);
    const [newMessage, setNewMessage] = React.useState('');
    const [conversationId, setConversationId] = React.useState(null);
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState(null);
    const [operationLogs, setOperationLogs] = React.useState([]);
    const messagesEndRef = React.useRef(null);
    const logsEndRef = React.useRef(null);

    // Load conversation ID from localStorage on component mount
    React.useEffect(() => {
        const savedId = localStorage.getItem('conversationId');
        if (savedId) {
            setConversationId(savedId);
            // Optionally load conversation history here if you implement that endpoint
        }
    }, []);

    // Scroll to bottom of messages whenever messages change
    React.useEffect(() => {
        scrollToBottom('messages');
    }, [messages]);

    // Scroll to bottom of logs whenever logs change
    React.useEffect(() => {
        scrollToBottom('logs');
    }, [operationLogs]);

    const scrollToBottom = (type) => {
        if (type === 'messages') {
            messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        } else if (type === 'logs') {
            logsEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        }
    };

    // Add new function to start a new conversation
    const startNewConversation = () => {
        // Clear existing conversation data
        setMessages([]);
        setConversationId(null);
        setOperationLogs([]);
        localStorage.removeItem('conversationId');
    };

    // Add log entry with timestamp
    const addLogEntry = (operation, executionTime, status = 'success') => {
        const timestamp = new Date().toISOString().split('T')[1].split('.')[0]; // HH:MM:SS
        setOperationLogs(logs => [
            ...logs,
            {
                timestamp,
                operation,
                executionTime,
                status
            }
        ]);
    };

    const sendMessage = async (e) => {
        e.preventDefault();
        if (!newMessage.trim()) return;

        // Add user message to UI immediately
        setMessages([...messages, { type: 'user', content: newMessage }]);
        setLoading(true);
        setError(null);

        // Start timer for overall request
        const startTime = performance.now();

        // Log the start of the operation
        addLogEntry('Sending message', 0, 'pending');

        try {
            const headers = {
                'Content-Type': 'application/json'
            };

            // Include conversation ID in headers if it exists
            if (conversationId) {
                headers['X-Conversation-ID'] = conversationId;
            }

            // Determine if we need to create a new conversation
            const createNew = conversationId === null;

            // Create the URL with query parameter if needed
            const url = createNew
                ? '/api/chat?createNew=true'
                : '/api/chat';

            const response = await fetch(url, {
                method: 'POST',
                headers,
                body: JSON.stringify({
                    message: newMessage,
                    userId: 'user123' // You might want to make this dynamic
                })
            });

            if (!response.ok) {
                throw new Error('Failed to send message');
            }

            // Calculate execution time
            const executionTime = Math.round(performance.now() - startTime);

            // Extract conversation ID from response
            const newConversationId = response.headers.get('X-Conversation-ID');
            if (newConversationId) {
                setConversationId(newConversationId);
                localStorage.setItem('conversationId', newConversationId);
            }

            const data = await response.json();

            // Add system response to UI
            setMessages(msgs => [...msgs, { type: 'system', content: data.message }]);
            setNewMessage('');

            // Check if there's execution time data in the response headers
            const serverExecutionTime = response.headers.get('X-Execution-Time');
            const executionTools = response.headers.get('X-Executed-Tools');

            // Log the completed operation
            addLogEntry(
                executionTools ? `Message processed (${executionTools})` : 'Message processed',
                serverExecutionTime || executionTime
            );

            // If we have detailed tool execution times
            if (data.executionDetails && Array.isArray(data.executionDetails)) {
                data.executionDetails.forEach(detail => {
                    addLogEntry(
                        `Tool: ${detail.tool}`,
                        detail.executionTime,
                        detail.status.toLowerCase()
                    );
                });
            }
        } catch (err) {
            console.error('Error:', err);
            setError('Failed to send message. Please try again.');

            // Log the failed operation
            const executionTime = Math.round(performance.now() - startTime);
            addLogEntry('Message failed', executionTime, 'error');
        } finally {
            setLoading(false);
        }
    };

    // Get status color class based on status
    const getStatusColorClass = (status) => {
        switch(status) {
            case 'error':
                return 'text-red-600';
            case 'pending':
                return 'text-yellow-500';
            case 'success':
                return 'text-green-600';
            default:
                return 'text-gray-600';
        }
    };

    return (
        <div className="flex h-screen p-4 bg-gray-50">
            {/* Main chat panel (60%) */}
            <div className="flex flex-col w-3/5 pr-2">
                <div className="flex items-center mb-4 p-3 bg-blue-600 text-white rounded-t-lg">
                    <Icons.MessageCircle className="mr-2" />
                    <h1 className="text-xl font-bold">LLMAO Chat</h1>
                    <button
                        onClick={startNewConversation}
                        className="ml-3 px-3 py-1 bg-green-500 hover:bg-green-600 text-white font-bold rounded-full flex items-center"
                        title="Start new conversation"
                    >
                        <Icons.PlusCircle size={16} className="mr-1" />
                        NEW
                    </button>
                    {conversationId && (
                        <span className="ml-auto text-xs opacity-75">ID: {conversationId.substring(0, 8)}...</span>
                    )}
                </div>

                <div className="flex-1 overflow-auto mb-4 p-4 bg-white rounded-lg shadow">
                    {messages.length === 0 ? (
                        <div className="flex flex-col items-center justify-center h-full text-gray-400">
                            <Icons.MessageCircle size={48} className="mb-2" />
                            <p>Send a message to start chatting</p>
                        </div>
                    ) : (
                        <div className="space-y-4">
                            {messages.map((msg, index) => (
                                <div
                                    key={index}
                                    className={`flex ${msg.type === 'user' ? 'justify-end' : 'justify-start'}`}
                                >
                                    <div
                                        className={`max-w-xs md:max-w-md lg:max-w-lg px-4 py-2 rounded-lg ${
                                            msg.type === 'user'
                                                ? 'bg-blue-600 text-white'
                                                : 'bg-gray-200 text-gray-800'
                                        }`}
                                    >
                                        {msg.content}
                                    </div>
                                </div>
                            ))}
                            <div ref={messagesEndRef} />
                        </div>
                    )}
                </div>

                {error && (
                    <div className="mb-4 p-3 bg-red-100 text-red-800 rounded-lg flex items-center">
                        <Icons.AlertCircle className="mr-2" size={16} />
                        {error}
                    </div>
                )}

                <form onSubmit={sendMessage} className="flex gap-2">
                    <input
                        type="text"
                        value={newMessage}
                        onChange={(e) => setNewMessage(e.target.value)}
                        placeholder="Type your message..."
                        className="flex-1 p-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                        disabled={loading}
                    />
                    <button
                        type="submit"
                        className="p-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
                        disabled={loading || !newMessage.trim()}
                    >
                        {loading ? (
                            <div className="w-6 h-6 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                        ) : (
                            <Icons.Send size={20} />
                        )}
                    </button>
                </form>
            </div>

            {/* Operation logs panel (40%) */}
            <div className="flex flex-col w-2/5 pl-2">
                <div className="flex items-center mb-4 p-3 bg-gray-700 text-white rounded-t-lg">
                    <Icons.Activity className="mr-2" />
                    <h1 className="text-xl font-bold">Operation Logs</h1>
                    <button
                        onClick={() => setOperationLogs([])}
                        className="ml-3 px-3 py-1 bg-gray-500 hover:bg-gray-600 text-white font-bold rounded-full flex items-center"
                        title="Clear logs"
                    >
                        Clear
                    </button>
                </div>

                <div className="flex-1 overflow-auto mb-4 p-4 bg-gray-800 text-gray-100 rounded-lg shadow font-mono text-sm">
                    {operationLogs.length === 0 ? (
                        <div className="flex flex-col items-center justify-center h-full text-gray-400">
                            <Icons.Clock size={48} className="mb-2" />
                            <p>No operations logged yet</p>
                        </div>
                    ) : (
                        <div className="space-y-1">
                            {operationLogs.map((log, index) => (
                                <div key={index} className="flex items-start">
                                    <span className="text-gray-400 mr-2">[{log.timestamp}]</span>
                                    <span className={`mr-2 ${getStatusColorClass(log.status)}`}>
                                        {log.status === 'success' ? '✓' : log.status === 'error' ? '✗' : '⟳'}
                                    </span>
                                    <span className="text-gray-200 flex-1">{log.operation}</span>
                                    <span className="text-yellow-300 ml-2">
                                        {log.executionTime}ms
                                    </span>
                                </div>
                            ))}
                            <div ref={logsEndRef} />
                        </div>
                    )}
                </div>

                {/* Stats summary */}
                <div className="bg-gray-700 text-white p-3 rounded-lg">
                    <h3 className="text-sm font-semibold mb-2">Stats Summary</h3>
                    <div className="grid grid-cols-2 gap-2 text-sm">
                        <div>
                            <p>Total Operations: {operationLogs.length}</p>
                            <p>Success: {operationLogs.filter(log => log.status === 'success').length}</p>
                        </div>
                        <div>
                            <p>Failed: {operationLogs.filter(log => log.status === 'error').length}</p>
                            <p>Avg Time: {operationLogs.length > 0 ?
                                Math.round(operationLogs.reduce((acc, log) => acc + (log.executionTime || 0), 0) / operationLogs.length) : 0}ms
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

// Render the app
ReactDOM.render(
    <Chatinterface />,
    document.getElementById('root')
);