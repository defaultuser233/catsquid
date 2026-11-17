package com.maoyou.typingpet.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class PetStateTest {
    private class FakeClock(var time: Long = 1_000L) {
        fun now(): Long = time
        fun advance(ms: Long) {
            time += ms
        }
    }

    private fun machine(clock: FakeClock) = PetStateMachine(now = clock::now)

    @Test
    fun initialStateIsIdleWithStepZero() {
        val machine = machine(FakeClock())
        assertEquals(PetState.IDLE, machine.state)
        assertEquals(0, machine.typingStep)
    }

    @Test
    fun typeEntersTypingAndIncrementsStep() {
        val machine = machine(FakeClock())
        assertEquals(PetState.TYPING, machine.onAction(KeyAction.Type))
        assertEquals(PetState.TYPING, machine.state)
        assertEquals(1, machine.typingStep)
        machine.onAction(KeyAction.Type)
        assertEquals(2, machine.typingStep)
    }

    @Test
    fun spaceAndExclamationEnterOwnStatesAndIncrementStep() {
        val space = machine(FakeClock())
        assertEquals(PetState.SPACE, space.onAction(KeyAction.Space))
        assertEquals(PetState.SPACE, space.state)
        assertEquals(1, space.typingStep)

        val exclamation = machine(FakeClock())
        assertEquals(PetState.EXCLAMATION, exclamation.onAction(KeyAction.Exclamation))
        assertEquals(PetState.EXCLAMATION, exclamation.state)
        assertEquals(1, exclamation.typingStep)
    }

    @Test
    fun deleteEntersDeleteWithoutIncrementingStep() {
        val machine = machine(FakeClock())
        machine.onAction(KeyAction.Type)
        assertEquals(1, machine.typingStep)

        assertEquals(PetState.DELETE, machine.onAction(KeyAction.Delete))
        assertEquals(PetState.DELETE, machine.state)
        assertEquals(1, machine.typingStep)

        machine.onAction(KeyAction.Delete)
        assertEquals(1, machine.typingStep)
    }

    @Test
    fun ignoreKeepsStateAndDoesNotExtendIdleTimer() {
        val clock = FakeClock()
        val machine = machine(clock)
        machine.onAction(KeyAction.Type)
        clock.advance(600)

        assertEquals(PetState.TYPING, machine.onAction(KeyAction.Ignore))
        assertEquals(PetState.TYPING, machine.state)
        assertEquals(1, machine.typingStep)

        clock.advance(99)
        assertEquals(PetState.TYPING, machine.tick())

        clock.advance(1)
        assertEquals(PetState.IDLE, machine.tick())
    }

    @Test
    fun tickStaysActiveJustBeforeDelayAndIdlesAtDelay() {
        val clock = FakeClock()
        val machine = machine(clock)
        machine.onAction(KeyAction.Type)

        clock.advance(699)
        assertEquals(PetState.TYPING, machine.tick())
        assertEquals(PetState.TYPING, machine.state)
        assertEquals(1, machine.typingStep)

        clock.advance(1)
        assertEquals(PetState.IDLE, machine.tick())
        assertEquals(PetState.IDLE, machine.state)
        assertEquals(0, machine.typingStep)
    }

    @Test
    fun typingResumesAtStepOneAfterIdle() {
        val clock = FakeClock()
        val machine = machine(clock)
        machine.onAction(KeyAction.Type)
        machine.onAction(KeyAction.Type)

        clock.advance(700)
        assertEquals(PetState.IDLE, machine.tick())
        assertEquals(0, machine.typingStep)

        clock.advance(5_000)
        assertEquals(PetState.TYPING, machine.onAction(KeyAction.Type))
        assertEquals(1, machine.typingStep)
    }

    @Test
    fun stateSwitchesKeepStepExceptDelete() {
        val machine = machine(FakeClock())
        machine.onAction(KeyAction.Type)
        assertEquals(1, machine.typingStep)

        assertEquals(PetState.SPACE, machine.onAction(KeyAction.Space))
        assertEquals(2, machine.typingStep)

        assertEquals(PetState.EXCLAMATION, machine.onAction(KeyAction.Exclamation))
        assertEquals(3, machine.typingStep)

        assertEquals(PetState.DELETE, machine.onAction(KeyAction.Delete))
        assertEquals(3, machine.typingStep)

        assertEquals(PetState.TYPING, machine.onAction(KeyAction.Type))
        assertEquals(4, machine.typingStep)
    }
}
