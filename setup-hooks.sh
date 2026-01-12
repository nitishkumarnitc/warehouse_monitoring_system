#!/bin/bash

# Setup git hooks for the project
echo "Setting up git hooks..."

# Configure git to use .githooks directory
git config core.hooksPath .githooks

echo "✅ Git hooks configured successfully!"
echo "   Hooks directory: .githooks/"
echo ""
echo "To disable hooks temporarily, use: git commit --no-verify"
