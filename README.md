# IntelliJ Plugin: AI-Powered Commit Message Generator

<!-- Plugin description -->
This IntelliJ IDEA plugin automatically generates Git commit messages based on your code changes using AI. It analyzes file diffs and produces concise and relevant commit messages to streamline your development workflow.
<!-- Plugin description end -->

## ✨ Features

- Adds a **"Generate Commit Message"** action to your commit dialog
- Analyzes staged code changes (before and after)
- Generates clear, simple commit messages using a remote AI API
- Falls back to basic auto-generated messages if no diff-based message is found

## 🚀 Getting Started

### Prerequisites

- IntelliJ IDEA (2022.1+)
- Git integration enabled
- An API endpoint that accepts code diffs and returns commit messages
- Set the following in your `BuildConfig`:
    - `API_URL`: The base URL for the model endpoint
    - `REMOTE_API_URL`: Optional proxy endpoint for calling the AI service
    - `API_TOKEN`: Your token to authenticate with the API

### Installation

1. Clone this repository:
   ```bash
   git clone https://github.com/yourusername/intellij-commit-message-generator.git
