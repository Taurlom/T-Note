package ru.taurlom.tnote.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteImageRefsTest {

    // ---------- extractAll ----------

    @Test
    fun `extractAll returns image urls in order of appearance`() {
        val markdown = "Текст ![](a/1.jpg) ещё ![alt](content://photo/2) и ![](a/3.png)"
        assertEquals(listOf("a/1.jpg", "content://photo/2", "a/3.png"), NoteImageRefs.extractAll(markdown))
    }

    @Test
    fun `extractAll ignores plain links`() {
        assertEquals(emptyList<String>(), NoteImageRefs.extractAll("смотри [тут](https://example.com)"))
    }

    @Test
    fun `extractAll of empty text is empty`() {
        assertEquals(emptyList<String>(), NoteImageRefs.extractAll(""))
    }

    // ---------- replace ----------

    @Test
    fun `replace swaps only mapped urls and keeps alt text`() {
        val markdown = "![](content://photo/1)\n![мясо](a/2.jpg)"
        val replaced = NoteImageRefs.replace(markdown, mapOf("content://photo/1" to "a/9.jpg"))
        assertEquals("![](a/9.jpg)\n![мясо](a/2.jpg)", replaced)
    }

    @Test
    fun `replace with empty map returns source`() {
        val markdown = "![](a/1.jpg)"
        assertEquals(markdown, NoteImageRefs.replace(markdown, emptyMap()))
    }

    // ---------- withAppendedMissing ----------

    @Test
    fun `withAppendedMissing appends unreferenced photos at the end`() {
        val result = NoteImageRefs.withAppendedMissing("текст ![](a/1.jpg)", listOf("a/1.jpg", "a/2.jpg"))
        assertEquals("текст ![](a/1.jpg)\n![](a/2.jpg)", result)
    }

    @Test
    fun `withAppendedMissing of fully referenced photos returns source`() {
        val markdown = "текст ![](a/1.jpg)"
        assertEquals(markdown, NoteImageRefs.withAppendedMissing(markdown, listOf("a/1.jpg")))
    }

    @Test
    fun `withAppendedMissing into empty text has no leading newline`() {
        assertEquals("![](a/1.jpg)", NoteImageRefs.withAppendedMissing("", listOf("a/1.jpg")))
    }
}
