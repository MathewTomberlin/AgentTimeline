# AgentTimeline Chat Interface

A minimalist chat interface for interacting with the AgentTimeline knowledge extraction and retrieval system.

## Files

- `chat.ps1` - Main PowerShell chat script
- `chat.bat` - Windows batch file wrapper for the PowerShell script
- `README.md` - This documentation file

## Usage

### PowerShell Script

```powershell
# Start chat with default session
.\chat.ps1

# Start chat with custom session ID
.\chat.ps1 -SessionId "my-session"

# Start chat without pause prompts
.\chat.ps1 -NoPause
```

### Batch File

```batch
# Start chat with default session
chat.bat

# Start chat with custom session ID
chat.bat -SessionId "my-session"
```

## Features

- **Minimalist Interface**: Clean, simple chat interface with colored prompts
- **Session Management**: Each conversation is tracked with a session ID
- **Knowledge Retrieval**: Uses the AgentTimeline knowledge extraction endpoint
- **Error Handling**: Graceful handling of server connection issues
- **Exit Commands**: Type `quit` or `exit` to end the conversation

## Requirements

- PowerShell 5.1 or higher
- AgentTimeline server running on `http://localhost:8080`
- Appropriate PowerShell execution policy (RemoteSigned recommended)

## Session Continuity

The chat interface maintains conversation context within the same session ID. Messages are stored and can be retrieved later using the timeline endpoints.

## Troubleshooting

If you encounter execution policy errors, run:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

If the server is not running, start it first using:
```powershell
.\scripts\start-app.ps1
```
