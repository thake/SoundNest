package io.github.thake.soundnest.spotify

/**
 * Thread-safe state machine tracking 402 `COMMAND_FAILED` refusal escalation steps
 * and distinct refused command kinds within a sliding window, extracted from [SpotifyPlayer].
 */
internal class RefusalLadder(
    private val kindWindowMs: Long = 10_000L,
) {
    private var step = 0
    private val refusedKinds = LinkedHashMap<String, Long>()

    @Synchronized
    fun nextStep(hasTrack: Boolean): Int {
        var next = ++step
        if (next == 1 && !hasTrack) next = ++step
        return next
    }

    @Synchronized
    fun reset() {
        step = 0
    }

    /**
     * Records a refused command kind at [nowMs] and returns `true` when at least 2 distinct
     * command kinds have been refused within [kindWindowMs] (indicating session-wide refusal).
     */
    @Synchronized
    fun recordRefusedKind(kind: String, nowMs: Long): Boolean {
        refusedKinds[kind] = nowMs
        refusedKinds.entries.removeIf { nowMs - it.value > kindWindowMs }
        return refusedKinds.size >= 2
    }

    @Synchronized
    fun sortedKinds(): List<String> = refusedKinds.keys.sorted()

    @Synchronized
    fun clearKinds() {
        refusedKinds.clear()
    }
}
