package com.example

import com.example.model.ClipType
import com.example.model.TimelineClip
import com.example.model.VideoProject
import com.example.ui.components.formatDurationShort
import com.example.ui.components.formatTimecode
import com.example.ui.components.resolveActiveClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun timeline_computes_effective_duration_and_resolves_playhead() {
        val clips = listOf(
            TimelineClip(
                clipId = "c1",
                type = ClipType.SCENE_CARD.name,
                title = "Intro",
                durationMs = 4000L,
                trimStartMs = 0L,
                trimEndMs = 4000L,
                speed = 2.0
            ),
            TimelineClip(
                clipId = "c2",
                type = ClipType.VIDEO.name,
                title = "Main",
                durationMs = 6000L,
                trimStartMs = 1000L,
                trimEndMs = 5000L,
                speed = 1.0
            )
        )
        val project = VideoProject(title = "Test", clips = clips)

        assertEquals(6000L, project.computedDurationMs())

        val firstSlice = resolveActiveClip(clips, 1200L)
        assertNotNull(firstSlice)
        assertEquals("c1", firstSlice?.clip?.clipId)
        assertEquals(1200L, firstSlice?.localOffsetMs)

        val secondSlice = resolveActiveClip(clips, 3500L)
        assertNotNull(secondSlice)
        assertEquals("c2", secondSlice?.clip?.clipId)
        assertEquals(1500L, secondSlice?.localOffsetMs)

        assertEquals("00:03.5", formatTimecode(3500L))
        assertEquals("6.0s", formatDurationShort(6000L))
    }

    @Test
    fun project_create_map_excludes_document_id() {
        val project = VideoProject(
            id = "doc_123",
            ownerId = "user_abc",
            title = "Demo"
        )
        val createMap = project.toCreateMap()
        assertFalse(createMap.containsKey("id"))
        assertEquals("user_abc", createMap["ownerId"])
        assertEquals("Demo", createMap["title"])
    }
}
