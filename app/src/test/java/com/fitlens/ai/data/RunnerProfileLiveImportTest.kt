package com.fitlens.ai.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Temporary end-to-end verification against the user's two public profiles. */
class RunnerProfileLiveImportTest {
    @Test fun importsCurrentBtrProfileThroughProductionClient() = runBlocking {
        val result = RunnerProfileImporter().import(
            "BTR", "https://www.my-btr.com/runners/019e8190-3121-7052-9561-037b2ac2d68c")
        assertTrue(result.score.orEmpty(), result.score.orEmpty().contains("409"))
        assertTrue(result.score.orEmpty(), result.score.orEmpty().contains("4989"))
    }

    @Test fun importsCurrentItraProfileThroughProductionClient() = runBlocking {
        val result = RunnerProfileImporter().import(
            "ITRA", "https://itra.run/RunnerSpace/petrov.aleksandar.7422083")
        assertTrue(result.score.orEmpty(), result.score.orEmpty().contains("324"))
        assertTrue(result.score.orEmpty(), result.score.orEmpty().contains("ITRA 1: 4"))
        assertTrue(result.score.orEmpty(), result.score.orEmpty().contains("ITRA 2: 1"))
    }
}
