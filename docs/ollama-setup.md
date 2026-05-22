# Ollama Setup Guide

## Installation

### macOS
```bash
brew install ollama
```

### Linux
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Windows
Download from https://ollama.com/download

## Start Ollama Service

### macOS/Linux
```bash
ollama serve
```

This starts the Ollama service in the foreground. For background operation, use system services.

### macOS (as service)
```bash
brew services start ollama
```

### Linux (as service)
```bash
systemctl start ollama
systemctl enable ollama
```

## Pull Required Model

For code generation, pull the CodeLlama model:

```bash
ollama pull codellama:13b
```

Alternative models:
- `deepseek-coder:6.7b` - Smaller, faster
- `codellama:7b` - Smaller version
- `codellama:34b` - Larger, more accurate

## Verify Installation

```bash
# Check Ollama version
ollama --version

# Check if service is running
curl http://localhost:11434/api/tags

# Test model
ollama run codellama:13b "Write a hello world in Kotlin"
```

## API Endpoint

Ollama runs on `http://localhost:11434` by default.

## Troubleshooting

### Service not running
```bash
# Check if port 11434 is in use
lsof -i :11434

# Restart service
brew services restart ollama  # macOS
systemctl restart ollama      # Linux
```

### Model not found
```bash
# List available models
ollama list

# Pull model again
ollama pull codellama:13b
```

### Port conflicts
Edit Ollama configuration to use a different port if 11434 is in use.
