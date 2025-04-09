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
    // Add the PlusCircle icon
    PlusCircle: (props) => (
        <svg xmlns="http://www.w3.org/2000/svg" width={props.size || 24} height={props.size || 24} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={props.className}>
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="16"></line>
            <line x1="8" y1="12" x2="16" y2="12"></line>
        </svg>
    )
};

// Chat Interface Component
function ChatInterface() {
    const [messages, setMessages] = React.useState([]);
    const [newMessage, setNewMessage] = React.useState('');
    const [conversationId, setConversationId] = React.useState(null);
    const [loading, setLoading] = React.useState(false);
    const [error, setError] = React.useState(null);
    const messagesEndRef = React.useRef(null);

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
        scrollToBottom();
    }, [messages]);

    const scrollToBottom = () => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    };

    // Add new function to start a new conversation
    const startNewConversation = () => {
        // Clear existing conversation data
        setMessages([]);
        setConversationId(null);
        localStorage.removeItem('conversationId');
    };

    const sendMessage = async (e) => {
        e.preventDefault();
        if (!newMessage.trim()) return;

        // Add user message to UI immediately
        setMessages([...messages, { type: 'user', content: newMessage }]);
        setLoading(true);
        setError(null);

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
        } catch (err) {
            console.error('Error:', err);
            setError('Failed to send message. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="flex flex-col h-screen max-w-3xl mx-auto p-4 bg-gray-50">
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
    );
}

// Render the app
ReactDOM.render(
    <ChatInterface />,
    document.getElementById('root')
);
