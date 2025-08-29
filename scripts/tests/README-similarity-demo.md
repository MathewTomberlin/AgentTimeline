# Similarity Search Demonstration Scripts

This directory contains demonstration scripts for AgentTimeline's Phase 4 vector similarity search functionality.

## Files

### `test-similarity-search-demo.ps1`
PowerShell script that demonstrates the complete vector similarity search workflow.

### `test-similarity-search-demo.bat`
Windows batch file wrapper that runs the PowerShell demonstration script.

## What the Demonstration Does

The demonstration script performs the following steps:

1. **Health Check**: Verifies the AgentTimeline application is running with Phase 4 features
2. **Send Message**: Sends a comprehensive message about AI and machine learning
3. **Vector Processing**: Shows how the system automatically:
   - Chunks the message into semantic pieces
   - Generates 768-dimensional vector embeddings
   - Stores everything in PostgreSQL
4. **Context-Aware Response**: Sends a follow-up question and shows how the assistant uses similarity search to provide informed responses
5. **Context Analysis**: Analyzes the assistant's response to confirm it used retrieved context from the original message

## How to Run

### Option 1: Using the Batch File (Recommended)
```cmd
.\scripts\tests\test-similarity-search-demo.bat
```

### Option 2: Using PowerShell Directly
```powershell
powershell -ExecutionPolicy Bypass -File ".\scripts\tests\test-similarity-search-demo.ps1"
```

### Option 3: With Custom Parameters
```powershell
powershell -ExecutionPolicy Bypass -File ".\scripts\tests\test-similarity-search-demo.ps1" -BaseUrl "http://localhost:8080/api/v1/timeline" -SessionId "my-custom-session"
```

## Prerequisites

1. **AgentTimeline Application**: Must be running on `http://localhost:8080`
2. **Phase 4 Features**: Application must have vector search functionality enabled
3. **Ollama Service**: Must be running for embedding generation
4. **PowerShell**: Windows PowerShell must be available

## Expected Output

The demonstration will show:

```
=== AgentTimeline Phase 4: Similarity Search Demonstration ===

1. Health Check...
   Status: UP, Phase: 4, Features: Message chaining, Conversation reconstruction, Chain validation, Vector embeddings, Similarity search

3. Sending comprehensive message about AI and Machine Learning...
   [SUCCESS] Message sent successfully!
   [MESSAGE] Message Content: "I want to learn about artificial intelligence..."

4. Vector Store Statistics...
   [STATS] Total Chunks: 3145, Unique Messages: 16, Unique Sessions: 3

5. Message Chunks Created...
   [CHUNKS] Found 201 chunks for the comprehensive message
   [SAMPLE] Sample chunks: [shows 3 example chunks]

6. Sending follow-up question to demonstrate context-aware response...
   [QUESTION] "Can you explain what vector embeddings are and how they work in AI systems?"
   [SUCCESS] Assistant response received!
   [ASSISTANT RESPONSE]: [Shows assistant's detailed response using context]
   [CONTEXT ANALYSIS] Assistant used context from previous message!
   [FOUND TERMS] artificial intelligence, machine learning, vector embeddings
   [SUCCESS] Similarity search provided relevant context for the response!

*** DEMONSTRATION COMPLETE! ***

What just happened:
1. [SEND] You sent a comprehensive message about AI and machine learning
2. [BRAIN] The system automatically broke your message into semantic chunks and generated embeddings
3. [CONTEXT] The assistant used similarity search to find relevant information from your previous message
4. [RESPONSE] Assistant provided an informed response using the retrieved context!
```

## Understanding the Results

### Vector Statistics
- **Total Chunks**: Number of text chunks stored in the vector database
- **Unique Messages**: Number of distinct messages processed
- **Unique Sessions**: Number of conversation sessions

### Message Chunks
- Shows how your message was broken into smaller, semantic pieces
- Each chunk is typically 100-500 characters with overlap for context preservation
- All chunks get 768-dimensional vector embeddings

### Context-Aware Response
- Shows how the assistant uses similarity search in real conversation scenarios
- The assistant automatically retrieves relevant context from previous messages
- Provides informed responses based on conversation history
- Demonstrates the practical value of vector similarity search

### Context Analysis
- Analyzes the assistant's response to identify terms from the original message
- Confirms that similarity search successfully provided relevant context
- Shows which specific concepts from your previous message were utilized
- Validates that the vector search system is working end-to-end

## Troubleshooting

### Application Not Running
```
Failed: The remote server returned an error: (500) Internal Server Error.
```
**Solution**: Start the AgentTimeline application first.

### Phase 4 Not Available
```
Features: Message chaining, Conversation reconstruction, Chain validation
```
**Solution**: Ensure Phase 4 vector search features are enabled.

### Ollama Not Running
```
[SUCCESS] Message sent successfully!
[ERROR] No chunks found
```
**Solution**: Start Ollama service and ensure the embedding model is loaded.

### Permission Issues
```
Execution Policy Error
```
**Solution**: Run with `-ExecutionPolicy Bypass` or adjust PowerShell execution policy.

## Next Steps

After running the demonstration, you can:

1. **Try Follow-up Questions**: Ask questions related to AI, machine learning, or vector embeddings
2. **Test Different Sessions**: Use different session IDs to see isolated conversation contexts
3. **Explore Other Endpoints**: Check out other test scripts in this directory
4. **Monitor Logs**: Check application logs to see vector search queries in action

## Integration with Chat

The similarity search demonstrated here is what powers context-aware responses in AgentTimeline. When you ask questions in chat, the assistant can:

- Search through your conversation history
- Find relevant information from previous messages
- Provide more informed and contextual responses
- Maintain continuity across your conversation

This demonstration shows exactly how that context retrieval works!
