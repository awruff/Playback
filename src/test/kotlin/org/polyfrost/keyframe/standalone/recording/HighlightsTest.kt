package org.polyfrost.keyframe.standalone.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val SELF = 1
private const val ENEMY = 2

class HighlightsTest {
	private val marks = mutableListOf<Pair<HighlightKind, Int>>()
	private val detector = HighlightDetector { kind, tick -> marks += kind to tick }

	private fun hit(now: Int, entity: Int = ENEMY) {
		detector.attacked(entity, now)
		detector.damaged(entity, SELF, now)
	}

	private fun hits(from: Int, count: Int) = repeat(count) { hit(from + it * 10) }

	private fun tick(now: Int, dead: Boolean = false, bed: Boolean = false) = detector.tick(now, { dead }, { bed })

	@Test
	fun fifthHitIsACombo() {
		hits(0, 4)
		assertEquals(emptyList(), marks)
		hit(40)
		assertEquals(listOf(HighlightKind.COMBO to 40), marks)
		hit(50)
		assertEquals(1, marks.size)
	}

	@Test
	fun hitsSpanningTheTimeoutStillCount() {
		hits(0, 4)
		hit(90)
		assertEquals(listOf(HighlightKind.COMBO to 90), marks)
	}

	@Test
	fun gapPastTheTimeoutRestartsTheCount() {
		hits(0, 4)
		hit(91)
		assertEquals(emptyList(), marks)
		hits(101, 3)
		assertEquals(emptyList(), marks)
		hit(131)
		assertEquals(listOf(HighlightKind.COMBO to 131), marks)
	}

	@Test
	fun takingDamageResetsTheCombo() {
		hits(0, 4)
		detector.damaged(SELF, SELF, 35)
		hit(40)
		assertEquals(emptyList(), marks)
		hits(50, 4)
		assertEquals(listOf(HighlightKind.COMBO to 80), marks)
	}

	@Test
	fun damageOnlyCountsSoonAfterAttackingThatEntity() {
		detector.attacked(ENEMY, 0)
		detector.damaged(ENEMY + 1, SELF, 1)
		detector.damaged(ENEMY, SELF, 11)
		tick(12, dead = true)
		assertEquals(emptyList(), marks)
		detector.attacked(ENEMY, 20)
		detector.damaged(ENEMY, SELF, 30)
		tick(31, dead = true)
		assertEquals(listOf(HighlightKind.KILL to 31), marks)
	}

	@Test
	fun killNeedsDeathWithinCreditWindow() {
		hit(0)
		tick(50)
		tick(100, dead = true)
		assertEquals(listOf(HighlightKind.KILL to 100), marks)
	}

	@Test
	fun deathAfterCreditWindowIsNotAKill() {
		hit(0)
		tick(101, dead = true)
		assertEquals(emptyList(), marks)
	}

	@Test
	fun brokenBedIsConfirmedTenTicksLater() {
		detector.bedBroken(7, 0)
		tick(9)
		assertEquals(emptyList(), marks)
		tick(10)
		assertEquals(listOf(HighlightKind.BED to 10), marks)
	}

	@Test
	fun bedStillThereIsDropped() {
		detector.bedBroken(7, 0)
		tick(10, bed = true)
		tick(20)
		assertEquals(emptyList(), marks)
	}

	@Test
	fun repeatsWithinFortyTicksAreDropped() {
		detector.message("Steve was slain by Alex", "Alex", 0)
		detector.message("Steve was slain by Alex", "Alex", 39)
		assertEquals(1, marks.size)
		detector.message("Steve was slain by Alex", "Alex", 40)
		assertEquals(listOf(HighlightKind.KILL to 0, HighlightKind.KILL to 40), marks)
	}

	@Test
	fun finalKillSharesTheKillWindow() {
		detector.message("Steve was slain by Alex", "Alex", 0)
		detector.message("Steve fell. FINAL KILL by Alex", "Alex", 10)
		assertEquals(listOf(HighlightKind.KILL to 0), marks)
		detector.message("Steve fell. FINAL KILL by Alex", "Alex", 40)
		detector.message("Steve was slain by Alex", "Alex", 50)
		assertEquals(listOf(HighlightKind.KILL to 0, HighlightKind.FINAL_KILL to 40), marks)
	}

	@Test
	fun kindsDedupeSeparately() {
		detector.message("Steve was slain by Alex", "Alex", 0)
		detector.message("Red Bed was destroyed by Alex", "Alex", 1)
		assertEquals(listOf(HighlightKind.KILL to 0, HighlightKind.BED to 1), marks)
	}

	@Test
	fun systemMessagesAreClassified() {
		assertEquals(HighlightKind.BED, classifyMessage("BED DESTRUCTION > Red Bed was torn apart by Alex!", "Alex"))
		assertEquals(HighlightKind.FINAL_KILL, classifyMessage("Steve was knocked off by Alex. FINAL KILL!", "Alex"))
		assertEquals(HighlightKind.KILL, classifyMessage("Steve was slain by Alex", "Alex"))
	}

	@Test
	fun unrelatedMessagesAreIgnored() {
		assertNull(classifyMessage("<Steve> killed by Alex: gg", "Alex"))
		assertNull(classifyMessage("Alex was slain by Alex", "Alex"))
		assertNull(classifyMessage("Steve was slain by Alexander", "Alex"))
		assertNull(classifyMessage("Steve was slain by Zombie", "Alex"))
	}

	@Test
	fun nameIsMatchedLiterally() {
		assertEquals(HighlightKind.KILL, classifyMessage("Steve was slain by A.ex", "A.ex"))
		assertNull(classifyMessage("Steve was slain by Alex", "A.ex"))
	}

	@Test
	fun clipEndsAfterTheEvent() {
		assertEquals(Clip(1000 + 60 - 600, 1060), highlightClip(1000, 3, 30))
	}

	@Test
	fun clipStartStopsAtZero() {
		assertEquals(Clip(0, 160), highlightClip(100, 3, 30))
	}

	@Test
	fun afterIsCappedAtClipLength() {
		assertEquals(Clip(1000, 1100), highlightClip(1000, 15, 5))
	}

	@Test
	fun zeroAfterEndsOnTheEvent() {
		assertEquals(Clip(500, 1000), highlightClip(1000, 0, 25))
	}
}
