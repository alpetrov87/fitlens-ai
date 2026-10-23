package com.fitlens.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarminTokenResponseTest {
    @Test fun parsesStandardOAuthResponse() {
        assertEquals("header.payload.signature", parseGarminAccessToken("""{"access_token":"header.payload.signature"}"""))
    }

    @Test fun parsesCamelCaseAndWrappedTokenResponses() {
        assertEquals("token-value-123456", parseGarminAccessToken("""{"data":{"accessToken":"token-value-123456"}}"""))
        assertEquals("token-value-123456", parseGarminAccessToken("""{"token":"token-value-123456"}"""))
    }

    @Test fun parsesPlainAndFormEncodedResponses() {
        assertEquals("header.payload.signature", parseGarminAccessToken(" header.payload.signature "))
        assertEquals("header.payload.signature", parseGarminAccessToken("token_type=Bearer&access_token=header.payload.signature"))
    }

    @Test fun doesNotTreatErrorMessagesAsTokens() {
        assertNull(parseGarminAccessToken("{\"message\":\"invalid key\"}"))
        assertNull(parseGarminAccessToken("Garmin token could not be created"))
    }
}
