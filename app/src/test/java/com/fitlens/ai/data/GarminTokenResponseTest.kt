package com.fitlens.ai.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarminTokenResponseTest {
    private val token = "header.${Base64.getUrlEncoder().withoutPadding().encodeToString("{\"exp\":4102444800}".toByteArray())}.signature"

    @Test fun acceptsRawGarminJwtResponseWithExpirationClaim() {
        assertEquals(token, parseGarminAccessToken(" $token\n"))
    }

    @Test fun rejectsResponsesWithoutJwtExpiration() {
        assertNull(parseGarminAccessToken("header.cGF5bG9hZA.signature"))
        assertNull(parseGarminAccessToken("Garmin token could not be created"))
    }
}
