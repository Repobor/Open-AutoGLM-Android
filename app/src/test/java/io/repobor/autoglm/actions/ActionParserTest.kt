package io.repobor.autoglm.actions

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ActionParser.
 */
class ActionParserTest {

    @Test
    fun testParseFinishWithSimpleMessage() {
        val response = """finish(message="Task completed successfully")"""
        val result = ActionParser.parseResponse(response)

        assertNotNull(result)
        assertEquals("finish", result!!["action"])
        assertEquals("Task completed successfully", result["message"])
    }

    @Test
    fun testParseFinishWithChineseQuotes() {
        // This is the actual failing case from the error log
        val response = """finish(message="任务已完成！我已成功打开淘宝并搜索"大疆无人机"。搜索结果页面显示了多款大疆无人机产品，包括DJI Mini 5 Pro、DJI Mini 4K、DJI Mini 3等型号，价格从1419元到4788元不等。您可以浏览这些搜索结果选择您需要的无人机产品。")"""
        val result = ActionParser.parseResponse(response)

        assertNotNull("Parser should successfully parse finish with Chinese quotes", result)
        assertEquals("finish", result!!["action"])
        assertTrue("Message should contain Chinese text", (result["message"] as String).contains("大疆无人机"))
    }

    @Test
    fun testParseFinishWithNestedQuotes() {
        val response = """finish(message="I found the item 'iPhone 15' with price $999")"""
        val result = ActionParser.parseResponse(response)

        assertNotNull(result)
        assertEquals("finish", result!!["action"])
        assertTrue((result["message"] as String).contains("iPhone 15"))
    }

    @Test
    fun testParseTapAction() {
        val response = """do(action="Tap", element=[500, 300])"""
        val result = ActionParser.parseResponse(response)

        assertNotNull(result)
        assertEquals("tap", result!!["action"])
        val element = result["element"] as List<*>
        assertEquals(500, element[0])
        assertEquals(300, element[1])
    }

    @Test
    fun testParseTypeAction() {
        val response = """do(action="Type", text="Hello World")"""
        val result = ActionParser.parseResponse(response)

        assertNotNull(result)
        assertEquals("type", result!!["action"])
        assertEquals("Hello World", result["text"])
    }

    @Test
    fun testParseTypeWithChineseText() {
        val response = """do(action="Type", text="大疆无人机")"""
        val result = ActionParser.parseResponse(response)

        assertNotNull(result)
        assertEquals("type", result!!["action"])
        assertEquals("大疆无人机", result["text"])
    }

    @Test
    fun testParseSwipeAction() {
        val response = """do(action="Swipe", start=[100, 500], end=[100, 200])"""
        val result = ActionParser.parseResponse(response)

        assertNotNull(result)
        assertEquals("swipe", result!!["action"])
        val start = result["start"] as List<*>
        val end = result["end"] as List<*>
        assertEquals(100, start[0])
        assertEquals(500, start[1])
        assertEquals(100, end[0])
        assertEquals(200, end[1])
    }

    @Test
    fun testParseLaunchAction() {
        val response = """do(action="Launch", app="微信")"""
        val result = ActionParser.parseResponse(response)

        assertNotNull(result)
        assertEquals("launch", result!!["action"])
        assertEquals("微信", result["app"])
    }

    @Test
    fun testParseBackAction() {
        val response = """do(action="Back")"""
        val result = ActionParser.parseResponse(response)

        assertNotNull(result)
        assertEquals("back", result!!["action"])
    }

    @Test
    fun testParseWithAnswerTags() {
        val response = """
            <answer>
            finish(message="Task completed")
            </answer>
        """.trimIndent()
        val result = ActionParser.parseResponse(response)

        assertNotNull(result)
        assertEquals("finish", result!!["action"])
    }

    @Test
    fun testParseWithThinkTags() {
        val response = """
            <think>I need to tap the search button</think>
            <answer>do(action="Tap", element=[500, 300])</answer>
        """.trimIndent()

        val thinking = ActionParser.extractThinking(response)
        assertNotNull(thinking)
        assertTrue(thinking!!.contains("search button"))

        val result = ActionParser.parseResponse(response)
        assertNotNull(result)
        assertEquals("tap", result!!["action"])
    }

    @Test
    fun testValidateFinishAction() {
        val action = mapOf(
            "action" to "finish",
            "message" to "Done"
        )
        assertTrue(ActionParser.validateAction(action))
    }

    @Test
    fun testValidateTapAction() {
        val action = mapOf(
            "action" to "tap",
            "element" to listOf(100, 200)
        )
        assertTrue(ActionParser.validateAction(action))
    }

    @Test
    fun testValidateInvalidTapAction() {
        val action = mapOf(
            "action" to "tap"
            // Missing element
        )
        assertFalse(ActionParser.validateAction(action))
    }

    @Test
    fun testGetActionDescription() {
        val tapAction = mapOf(
            "action" to "tap",
            "element" to listOf(100, 200)
        )
        val description = ActionParser.getActionDescription(tapAction)
        assertTrue(description.contains("Tap"))
        assertTrue(description.contains("100"))

        val finishAction = mapOf(
            "action" to "finish",
            "message" to "All done"
        )
        val finishDesc = ActionParser.getActionDescription(finishAction)
        assertTrue(finishDesc.contains("Finish"))
        assertTrue(finishDesc.contains("All done"))
    }

    @Test
    fun testParseInvalidResponse() {
        val response = """This is just some random text without any action"""
        val result = ActionParser.parseResponse(response)

        assertNull("Should return null for invalid response", result)
    }

    @Test
    fun testParseEmptyResponse() {
        val response = ""
        val result = ActionParser.parseResponse(response)

        assertNull("Should return null for empty response", result)
    }
}
