package com.fitlens.ai.data

import org.junit.Assert.*
import org.junit.Test

class ChatGptProjectUrlTest {
    @Test fun acceptsProjectLinks() {
        assertTrue(isChatGptProjectUrl("https://chatgpt.com/g/g-p-123abc-fitlens/project"))
    }
    @Test fun rejectsOtherDestinationsAndConversations() {
        listOf("http://chatgpt.com/g/g-p-abc/project", "https://chatgpt.com.evil.test/g/g-p-abc/project",
            "https://evil.test/g/g-p-abc/project", "https://chatgpt.com/c/abc",
            "https://user@chatgpt.com/g/g-p-abc/project", "https://chatgpt.com/g/g-p-abc/project?redirect=other")
            .forEach { assertFalse(it, isChatGptProjectUrl(it)) }
    }
}
