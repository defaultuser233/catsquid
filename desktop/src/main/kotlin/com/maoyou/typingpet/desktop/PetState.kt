package com.maoyou.typingpet.desktop

/** 打鍵イベントの種類 */
sealed interface KeyAction {
    data object Type : KeyAction
    data object Delete : KeyAction
    data object Space : KeyAction
    data object Exclamation : KeyAction
    data object Ignore : KeyAction
}

enum class PetState { IDLE, TYPING, DELETE, SPACE, EXCLAMATION }

/**
 * 打鍵に応じた状態と入力ステップを管理する。UI スレッド専用。
 * 入力が idleDelayMs 止まると待機に戻り、ステップは 0 に戻る。
 */
class PetStateMachine(
    private val idleDelayMs: Long = 700L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var state: PetState = PetState.IDLE
        private set

    var typingStep: Int = 0
        private set

    private var lastActionAt: Long = 0L

    fun onAction(action: KeyAction): PetState {
        if (action == KeyAction.Ignore) return state
        lastActionAt = now()
        state = when (action) {
            KeyAction.Type -> PetState.TYPING
            KeyAction.Delete -> PetState.DELETE
            KeyAction.Space -> PetState.SPACE
            KeyAction.Exclamation -> PetState.EXCLAMATION
            KeyAction.Ignore -> state
        }
        if (action != KeyAction.Delete) typingStep++
        return state
    }

    fun tick(): PetState {
        if (state != PetState.IDLE && now() - lastActionAt >= idleDelayMs) {
            state = PetState.IDLE
            typingStep = 0
        }
        return state
    }
}
