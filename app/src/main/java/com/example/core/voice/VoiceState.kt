package com.example.core.voice

enum class VoiceState {
    SLEEPING,
    WAKE_DETECTED,
    LISTENING,
    PROCESSING,
    ACTING,
    SPEAKING,
    WAITING_FOR_CONFIRMATION,
    CANCELLED;

    fun isInteractive(): Boolean = this == LISTENING || this == WAITING_FOR_CONFIRMATION

    fun toDisplayLabel(): String = when (this) {
        SLEEPING -> "SLEEPING"
        WAKE_DETECTED -> "WAKE DETECTED"
        LISTENING -> "LISTENING..."
        PROCESSING -> "THINKING..."
        ACTING -> "EXECUTING..."
        SPEAKING -> "SPEAKING..."
        WAITING_FOR_CONFIRMATION -> "WAITING FOR CONFIRMATION"
        CANCELLED -> "CANCELLED"
    }
}
