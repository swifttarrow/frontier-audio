package com.jarvis.gateway.audio

/** User interrupted during LLM streaming or TTS; turn should end without treating as a hard error. */
class TurnInterruptedException : Exception()
