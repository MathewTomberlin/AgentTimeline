# AgentTimeline Phase 2 Test Scripts

This directory contains comprehensive test scripts for the Phase 2 Enhanced Message Storage and Retrieval functionality.

## 🎯 Phase 2 Features Tested

Phase 2 introduces message chaining, conversation reconstruction, chain validation, and repair capabilities:

- **Message Chaining**: Messages are linked with parent-child relationships
- **Conversation Reconstruction**: Efficient reconstruction using message chains vs timestamp sorting
- **Chain Validation**: Integrity checking for message chains
- **Chain Repair**: Automatic repair of broken message chains
- **Statistics**: Comprehensive analytics across all sessions

## 📋 Available Test Scripts

### New Phase 2 Test Scripts

#### 1. `test-conversation-reconstruction.ps1` / `test-conversation-reconstruction.bat`
**Purpose**: Demonstrates conversation reconstruction using message chains
**What it does**:
- Creates a multi-turn conversation with message chaining
- Compares chain-based vs timestamp-based reconstruction
- Shows message chain structure and relationships
- Validates conversation integrity

**Usage**:
```powershell
# PowerShell
.\test-conversation-reconstruction.ps1 -SessionId "demo-session"

# Batch
test-conversation-reconstruction.bat -SessionId "demo-session"
```

#### 2. `test-chain-validation.ps1` / `test-chain-validation.bat`
**Purpose**: Tests message chain validation functionality
**What it does**:
- Creates test conversations
- Validates message chain integrity
- Tests validation endpoint responses
- Demonstrates chain structure analysis

**Usage**:
```powershell
# PowerShell
.\test-chain-validation.ps1 -SessionId "validation-test"

# Batch
test-chain-validation.bat -SessionId "validation-test"
```

#### 3. `test-chain-repair.ps1` / `test-chain-repair.bat`
**Purpose**: Tests chain repair functionality
**What it does**:
- Tests repair on valid chains
- Demonstrates repair reporting
- Tests edge case scenarios
- Shows before/after repair comparison

**Usage**:
```powershell
# PowerShell
.\test-chain-repair.ps1 -SessionId "repair-test"

# Batch
test-chain-repair.bat -SessionId "repair-test"
```

#### 4. `test-chain-statistics.ps1` / `test-chain-statistics.bat`
**Purpose**: Tests comprehensive statistics functionality
**What it does**:
- Creates multiple conversation sessions
- Generates varied chain structures
- Tests statistics endpoint
- Validates statistics accuracy
- Demonstrates real-time updates

**Usage**:
```powershell
# PowerShell
.\test-chain-statistics.ps1 -SessionsToCreate 3

# Batch
test-chain-statistics.bat -SessionsToCreate 3
```

#### 5. `test-phase2-demo.ps1` / `test-phase2-demo.bat`
**Purpose**: Complete Phase 2 demonstration
**What it does**:
- Rich multi-turn conversation creation
- Complete chain reconstruction showcase
- Validation and repair demonstration
- Statistics overview
- Final conversation flow display

**Usage**:
```powershell
# PowerShell
.\test-phase2-demo.ps1 -SessionId "complete-demo"

# Batch
test-phase2-demo.bat -SessionId "complete-demo"
```

### Updated Existing Scripts

#### Updated `test-chat.ps1`
- Now works with new Message model format
- Shows role, content, parent ID, and metadata
- Compatible with Phase 2 message chaining

#### Updated `test-get-session-messages.ps1`
- Displays messages in new Message format
- Shows role icons and parent relationships
- Compatible with Phase 2 structure

## 🚀 Quick Start Guide

### Prerequisites
1. **Application Running**: Make sure AgentTimeline is running on `http://localhost:8080`
2. **Ollama Available**: Ensure Ollama is running and accessible
3. **PowerShell Execution**: PowerShell execution policy allows script running

### Running Tests

#### Option 1: Individual Tests
```bash
# Test conversation reconstruction
./test-conversation-reconstruction.ps1

# Test chain validation
./test-chain-validation.ps1

# Complete Phase 2 demo
./test-phase2-demo.ps1
```

#### Option 2: Using Batch Files (Windows)
```cmd
# Test conversation reconstruction
test-conversation-reconstruction.bat

# Test chain validation
test-chain-validation.bat

# Complete Phase 2 demo
test-phase2-demo.bat
```

#### Option 3: Quiet Mode for CI/CD
```bash
# Run tests without interactive prompts
./test-phase2-demo.ps1 -Quiet -NoPause
```

## 📊 Understanding Test Results

### Conversation Reconstruction
The test will show:
- ✅ **Chain-based reconstruction** using message parent-child relationships
- 📊 **Comparison** with timestamp-based method
- 🔗 **Chain structure** visualization
- ✅ **Integrity validation** results

### Chain Validation
The test demonstrates:
- 🔍 **Chain integrity** checking
- 📝 **Validation results** with detailed analysis
- 🌳 **Message tree structure** visualization
- ✅ **Parent-child relationship** validation

### Chain Repair
The test shows:
- 🔧 **Automatic repair** capabilities
- 📋 **Repair reporting** with details
- ⚠️ **Edge case handling**
- ✅ **Post-repair validation**

### Statistics
The test provides:
- 📈 **Comprehensive analytics** across sessions
- 📊 **Validity rates** and chain health metrics
- 🎯 **Per-session statistics** with detailed breakdown
- 🔄 **Real-time updates** demonstration

## 🎯 What Each Test Demonstrates

| Test | Message Chaining | Reconstruction | Validation | Repair | Statistics |
|------|-----------------|----------------|------------|--------|------------|
| **Conversation Reconstruction** | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Chain Validation** | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Chain Repair** | ✅ | ❌ | ✅ | ✅ | ❌ |
| **Chain Statistics** | ✅ | ❌ | ✅ | ❌ | ✅ |
| **Phase 2 Demo** | ✅ | ✅ | ✅ | ✅ | ✅ |

## 🔧 Troubleshooting

### Common Issues

#### Application Not Running
```
Error: Connection error
Solution: Start the Spring Boot application first
```

#### Ollama Not Available
```
Error: Failed to send message
Solution: Ensure Ollama is running and accessible
```

#### Permission Issues
```powershell
# Allow script execution
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Test Data Cleanup
Tests create sessions with specific prefixes:
- `phase2-demo-session`
- `validation-test-session`
- `repair-test-session`
- `stats-session-*`

## 📝 Script Parameters

### Common Parameters
- `-Quiet`: Suppress detailed output
- `-NoPause`: Skip interactive prompts
- `-SessionId`: Specify custom session ID

### Test-Specific Parameters
- `test-conversation-reconstruction.ps1`: `-MessageCount` (default: 3)
- `test-chain-statistics.ps1`: `-SessionsToCreate` (default: 3)

## 🎉 Expected Results

When running the Phase 2 demo, you should see:

1. **Multi-turn conversation** creation with proper chaining
2. **Message reconstruction** that follows the exact conversation flow
3. **Chain validation** confirming all relationships are intact
4. **Repair functionality** working on edge cases
5. **Comprehensive statistics** showing system health
6. **Clear conversation flow** demonstrating the Phase 2 improvements

## 📚 Additional Resources

- [IMPLEMENTATION_GUIDE.md](../IMPLEMENTATION_GUIDE.md) - Phase 2 implementation details
- [README.md](../README.md) - Project overview
- API Documentation - Available endpoints for Phase 2 features

---

**🎊 Phase 2 Testing Complete!**

These scripts provide comprehensive testing coverage for all Phase 2 features, allowing you to clearly see conversation reconstruction in action and validate the enhanced message storage and retrieval capabilities.
