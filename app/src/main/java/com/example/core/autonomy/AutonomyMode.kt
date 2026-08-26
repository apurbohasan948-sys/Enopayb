package com.example.core.autonomy

enum class AutonomyMode(val label: String, val description: String) {
    MANUAL("MANUAL", "Only execute direct user commands. No autonomous or background tasks run without direct prompt."),
    ASSISTED("ASSISTED", "Execute multi-step tasks autonomously, but request user confirmation for any sensitive actions."),
    AUTONOMOUS("AUTONOMOUS", "Execute approved low-risk background and scheduled tasks autonomously. Highly sensitive actions still require approval.")
}
