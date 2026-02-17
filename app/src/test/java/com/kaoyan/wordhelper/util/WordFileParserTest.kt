package com.kaoyan.wordhelper.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordFileParserTest {

    @Test
    fun parse_tabSeparatedFourColumns() {
        val text = "abandon\t[abandon]\tvt. give up\tHe abandoned the plan."

        val drafts = WordFileParser.parse(text)

        assertEquals(1, drafts.size)
        assertEquals("abandon", drafts[0].word)
        assertEquals("[abandon]", drafts[0].phonetic)
        assertEquals("vt. give up", drafts[0].meaning)
        assertEquals("He abandoned the plan.", drafts[0].example)
    }

    @Test
    fun parse_csvWithQuotedMeaningContainingComma() {
        val text = """target,[target],"n. objective, goal""""

        val drafts = WordFileParser.parse(text)

        assertEquals(1, drafts.size)
        assertEquals("target", drafts[0].word)
        assertEquals("[target]", drafts[0].phonetic)
        assertEquals("n. objective, goal", drafts[0].meaning)
    }

    @Test
    fun parse_maimemoPackedWordMeaningStream() {
        val text = """
            a,"n. letter A"
            an,article abandon,"n. surrender vt. leave" according to,prep. based on
        """.trimIndent()

        val drafts = WordFileParser.parse(text)

        assertEquals(4, drafts.size)
        assertEquals("a", drafts[0].word)
        assertEquals("n. letter A", drafts[0].meaning)
        assertEquals("an", drafts[1].word)
        assertEquals("article", drafts[1].meaning)
        assertEquals("abandon", drafts[2].word)
        assertTrue(drafts[2].meaning.contains("surrender"))
        assertEquals("according to", drafts[3].word)
        assertEquals("prep. based on", drafts[3].meaning)
    }

    @Test
    fun parse_standardCsvKeepsPhoneticMeaningAndExampleColumns() {
        val text = "abandon,[abandon],vt. give up,He abandoned the plan."

        val drafts = WordFileParser.parse(text)

        assertEquals(1, drafts.size)
        assertEquals("abandon", drafts[0].word)
        assertEquals("[abandon]", drafts[0].phonetic)
        assertEquals("vt. give up", drafts[0].meaning)
        assertEquals("He abandoned the plan.", drafts[0].example)
    }

    @Test
    fun parse_csvQuotedMeaningWithLineBreak_keepsSingleEntry() {
        val text = """
            a,"n. 字母A；第一流的；学业成绩达最高标准的评价符号
            abbr. 安（ampere）"
            abandon,"vt. 放弃"
        """.trimIndent()

        val drafts = WordFileParser.parse(text)

        assertEquals(2, drafts.size)
        assertEquals("a", drafts[0].word)
        assertTrue(drafts[0].meaning.contains("学业成绩达最高标准的评价符号"))
        assertTrue(drafts[0].meaning.contains("abbr. 安（ampere）"))
        assertEquals("abandon", drafts[1].word)
    }

    @Test
    fun parse_csvUnquotedMeaningContinuation_mergesIntoPreviousMeaning() {
        val text = """
            a,n. 字母A；第一流的；学业成绩达最高标准的评价符号
            abbr. 安（ampere）
            abandon,vt. 放弃
        """.trimIndent()

        val drafts = WordFileParser.parse(text)

        assertEquals(2, drafts.size)
        assertEquals("a", drafts[0].word)
        assertTrue(drafts[0].meaning.contains("学业成绩达最高标准的评价符号"))
        assertTrue(drafts[0].meaning.contains("abbr. 安（ampere）"))
        assertEquals("abandon", drafts[1].word)
        assertEquals("vt. 放弃", drafts[1].meaning)
    }

    @Test
    fun parse_jsonWithDataArray_mapsRichFields() {
        val text = """
            {
              "code": 200,
              "data": [
                {
                  "word": "abandon",
                  "ukphone": "ə'bænd(ə)n",
                  "usphone": "ə'bændən",
                  "translations": [
                    { "pos": "v", "tran_cn": "放弃" },
                    { "pos": "n", "tran_cn": "放任" }
                  ],
                  "sentences": [
                    { "s_content": "He abandoned the plan.", "s_cn": "他放弃了计划。" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val drafts = WordFileParser.parse(text)

        assertEquals(1, drafts.size)
        assertEquals("abandon", drafts[0].word)
        assertEquals("[ə'bænd(ə)n]", drafts[0].phonetic)
        assertEquals("v. 放弃；n. 放任", drafts[0].meaning)
        assertEquals("He abandoned the plan.\n他放弃了计划。", drafts[0].example)
        assertEquals("", drafts[0].phrases)
        assertEquals("", drafts[0].synonyms)
        assertEquals("", drafts[0].relWords)
    }

    @Test
    fun parse_jsonWithDataObject_mapsSingleEntry() {
        val text = """
            {
              "data": {
                "word": "ability",
                "usphone": "ə'bɪləti",
                "phrases": [
                  { "p_content": "to the best of one's ability", "p_cn": "尽某人所能" }
                ]
              }
            }
        """.trimIndent()

        val drafts = WordFileParser.parse(text)

        assertEquals(1, drafts.size)
        assertEquals("ability", drafts[0].word)
        assertEquals("[ə'bɪləti]", drafts[0].phonetic)
        assertEquals("to the best of one's ability（尽某人所能）", drafts[0].meaning)
        assertEquals("to the best of one's ability（尽某人所能）", drafts[0].phrases)
    }

    @Test
    fun parse_jsonTopLevelArray_mapsEntries() {
        val text = """
            [
              {
                "word": "absolute",
                "synonyms": [
                  {
                    "pos": "adj",
                    "tran": "完全的",
                    "Hwds": [
                      { "word": "complete" },
                      { "word": "total" }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val drafts = WordFileParser.parse(text)

        assertEquals(1, drafts.size)
        assertEquals("absolute", drafts[0].word)
        assertEquals("adj: complete, total（完全的）", drafts[0].meaning)
        assertEquals("adj: complete, total（完全的）", drafts[0].synonyms)
    }

    @Test
    fun parse_jsonRelWordsIncludesTran() {
        val text = """
            {
              "data": {
                "word": "abandon",
                "relWords": [
                  {
                    "Pos": "n",
                    "Hwds": [
                      { "hwd": "abandonment", "tran": "抛弃；放纵" }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val drafts = WordFileParser.parse(text)

        assertEquals(1, drafts.size)
        assertEquals("abandon", drafts[0].word)
        assertEquals("n: abandonment（抛弃；放纵）", drafts[0].meaning)
        assertEquals("n: abandonment（抛弃；放纵）", drafts[0].relWords)
    }
}
