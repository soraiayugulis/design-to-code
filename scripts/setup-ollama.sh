#!/bin/bash

set -e

echo "=== Ollama Setup Script ==="

# Detect OS
OS="$(uname -s)"
case "${OS}" in
    Linux*)     machine=Linux;;
    Darwin*)    machine=Mac;;
    CYGWIN*)    machine=Cygwin;;
    MINGW*)     machine=MinGw;;
    *)          machine="UNKNOWN:${OS}"
esac

echo "Detected OS: ${machine}"

# Check if Ollama is installed
if command -v ollama &> /dev/null; then
    echo "✓ Ollama is already installed"
    ollama --version
else
    echo "✗ Ollama is not installed"
    
    if [ "$machine" = "Mac" ]; then
        echo "Installing Ollama via Homebrew..."
        brew install ollama
    elif [ "$machine" = "Linux" ]; then
        echo "Installing Ollama via curl..."
        curl -fsSL https://ollama.com/install.sh | sh
    else
        echo "Unsupported OS. Please install Ollama manually from https://ollama.com/download"
        exit 1
    fi
fi

# Check if Ollama service is running
if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
    echo "✓ Ollama service is running"
else
    echo "✗ Ollama service is not running"
    echo "Starting Ollama service..."
    
    if [ "$machine" = "Mac" ]; then
        brew services start ollama
    elif [ "$machine" = "Linux" ]; then
        systemctl start ollama
        systemctl enable ollama
    else
        echo "Please start Ollama service manually: ollama serve"
        exit 1
    fi
    
    # Wait for service to start
    echo "Waiting for Ollama service to start..."
    sleep 5
    
    if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
        echo "✓ Ollama service is now running"
    else
        echo "✗ Failed to start Ollama service"
        exit 1
    fi
fi

# Pull CodeLlama model
echo "Pulling CodeLlama 13b model (this may take a while)..."
ollama pull codellama:13b

echo "✓ CodeLlama 13b model downloaded"

# Verify
echo "=== Verification ==="
echo "Ollama version:"
ollama --version
echo ""
echo "Available models:"
ollama list
echo ""
echo "Testing model (simple prompt):"
ollama run codellama:13b "print('hello')" 2>/dev/null || echo "Model test completed"

echo ""
echo "=== Ollama Setup Complete ==="
echo "API endpoint: http://localhost:11434"
