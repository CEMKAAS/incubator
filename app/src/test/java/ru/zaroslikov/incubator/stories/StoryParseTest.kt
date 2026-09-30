package ru.zaroslikov.incubator.stories

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryParseTest {

    private val base = "http://10.0.2.2:3000"

    private fun parse(json: String) = StoryParser.parseStories(JSONArray(json), base)

    @Test
    fun parsesServerResponse() {
        val stories = parse(
            """
            [{"id":"a","title":"Закладка яиц","previewUrl":"https://picsum.photos/p.jpg",
              "slides":[{"type":"image","mediaUrl":"https://x/1.jpg","durationMs":7000,"text":"Текст"},
                        {"type":"video","mediaUrl":"https://x/2.mp4","durationMs":5000,
                         "buttonText":"Открыть","buttonUrl":"https://example.ru"}],
              "viewed":true,"startsAt":null,"endsAt":null}]
            """
        )
        assertEquals(1, stories.size)
        val story = stories.single()
        assertEquals("a", story.id)
        assertEquals("Закладка яиц", story.title)
        assertTrue(story.viewed)
        assertEquals(2, story.slides.size)
        assertEquals(SlideType.IMAGE, story.slides[0].type)
        assertEquals(7000L, story.slides[0].durationMs)
        assertEquals("Текст", story.slides[0].text)
        assertNull(story.slides[0].buttonUrl)
        assertEquals(SlideType.VIDEO, story.slides[1].type)
        assertEquals("Открыть", story.slides[1].buttonText)
        assertEquals("https://example.ru", story.slides[1].buttonUrl)
    }

    @Test
    fun missingViewedIsNotViewed() {
        val story = parse("""[{"id":"a","title":"t","slides":[{"type":"image","mediaUrl":"https://x/1.jpg"}]}]""").single()
        assertFalse(story.viewed)
        assertEquals(StoryParser.DEFAULT_DURATION_MS, story.slides.single().durationMs)
    }

    @Test
    fun dropsUnknownSlidesAndEmptyStories() {
        val stories = parse(
            """
            [{"id":"a","title":"t","slides":[{"type":"gif","mediaUrl":"https://x/1.gif"},
                                              {"type":"image","mediaUrl":""},
                                              {"type":"image","mediaUrl":"https://x/ok.jpg"}]},
             {"id":"b","title":"пустая","slides":[{"type":"audio","mediaUrl":"https://x/1.mp3"}]},
             {"title":"без id","slides":[{"type":"image","mediaUrl":"https://x/1.jpg"}]}]
            """
        )
        assertEquals(listOf("a"), stories.map { it.id })
        assertEquals(listOf("https://x/ok.jpg"), stories.single().slides.map { it.mediaUrl })
    }

    @Test
    fun clampsDuration() {
        val slides = parse(
            """[{"id":"a","title":"t","slides":[{"type":"image","mediaUrl":"https://x/1","durationMs":10},
                                                  {"type":"image","mediaUrl":"https://x/2","durationMs":999999}]}]"""
        ).single().slides
        assertEquals(1_000L, slides[0].durationMs)
        assertEquals(60_000L, slides[1].durationMs)
    }

    @Test
    fun buttonNeedsAllowedUrlAndGetsDefaultText() {
        val slides = parse(
            """[{"id":"a","title":"t","slides":[
                {"type":"image","mediaUrl":"https://x/1","buttonUrl":"incubator://measure/5"},
                {"type":"image","mediaUrl":"https://x/2","buttonText":"Жми","buttonUrl":"intent://evil"},
                {"type":"image","mediaUrl":"https://x/3","buttonText":"Только текст"}]}]"""
        ).single().slides
        assertEquals("incubator://measure/5", slides[0].buttonUrl)
        assertEquals(StoryParser.DEFAULT_BUTTON_TEXT, slides[0].buttonText)
        assertNull(slides[1].buttonUrl)
        assertNull(slides[1].buttonText)
        assertNull(slides[2].buttonUrl)
        assertNull(slides[2].buttonText)
    }

    @Test
    fun allowedButtonSchemes() {
        assertTrue(StoryParser.isAllowedButtonUrl("https://vk.ru"))
        assertTrue(StoryParser.isAllowedButtonUrl("HTTP://vk.ru"))
        assertTrue(StoryParser.isAllowedButtonUrl("incubator://measure/1"))
        assertFalse(StoryParser.isAllowedButtonUrl("myferma://animal/add"))
        assertFalse(StoryParser.isAllowedButtonUrl("file:///sdcard/x"))
        assertFalse(StoryParser.isAllowedButtonUrl("javascript:alert(1)"))
        assertFalse(StoryParser.isAllowedButtonUrl("vk.ru"))
    }

    @Test
    fun resolvesRelativeAndLoopbackMedia() {
        assertEquals("$base/uploads/a.jpg", StoryParser.resolveMediaUrl("/uploads/a.jpg", base))
        assertEquals("$base/uploads/a.jpg", StoryParser.resolveMediaUrl("$base/uploads/a.jpg", "$base/"))
        assertEquals("$base/uploads/a.jpg?v=2", StoryParser.resolveMediaUrl("http://localhost:3000/uploads/a.jpg?v=2", base))
        assertEquals("$base/uploads/a.jpg", StoryParser.resolveMediaUrl("http://127.0.0.1:3000/uploads/a.jpg", base))
        assertEquals("https://cdn.ru/a.jpg", StoryParser.resolveMediaUrl("https://cdn.ru/a.jpg", base))
        // Без адреса сервера переписывать не на что.
        assertEquals("http://localhost:3000/a.jpg", StoryParser.resolveMediaUrl("http://localhost:3000/a.jpg", ""))
    }

    @Test
    fun previewFallsBackToFirstImageSlide() {
        val story = parse(
            """[{"id":"a","title":"t","previewUrl":null,"slides":[
                {"type":"video","mediaUrl":"https://x/v.mp4"},{"type":"image","mediaUrl":"https://x/i.jpg"}]}]"""
        ).single()
        assertNull(story.previewUrl)
        assertEquals("https://x/i.jpg", story.coverUrl)
    }

    @Test
    fun localViewsMarkAndReorderStably() {
        fun story(id: String, viewed: Boolean) = Story(id, id, null, listOf(
            StorySlide(SlideType.IMAGE, "https://x", 5000, null, null, null)
        ), viewed)
        val server = listOf(story("a", false), story("b", false), story("c", true), story("d", false))
        val merged = StoryParser.withLocalViews(server, setOf("b"))
        assertEquals(listOf("a", "d", "b", "c"), merged.map { it.id })
        assertEquals(listOf(false, false, true, true), merged.map { it.viewed })
    }
}
