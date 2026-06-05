package com.example.myapplication.quality.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedRuleSetParserTest {
    @Test
    fun parse_validRuleSet_exposesVersionSourceAndSeverity() {
        val ruleSet = EmbeddedRuleSetParser.parse(validRuleSetJson())

        assertEquals(1, ruleSet.schemaVersion)
        assertEquals("2026.05-initial", ruleSet.ruleSetVersion)
        assertEquals(RuleSourceKind.BASE_SNAPSHOT, ruleSet.sources.single().kind)
        assertEquals(RuleSeverity.MANDATORY, ruleSet.rules.single().severity)
        assertEquals(listOf("XIAN", "YD_ID"), ruleSet.rules.single().locatorFields)
        assertTrue(ruleSet.rules.single().enabled)
    }

    @Test
    fun parse_missingEnabled_defaultsToTrueForBackwardCompatibility() {
        val json = validRuleSetJson().replace("          \"enabled\": true,\n", "")

        val ruleSet = EmbeddedRuleSetParser.parse(json)

        assertTrue(ruleSet.rules.single().enabled)
    }

    @Test
    fun parse_nonBooleanEnabled_rejectsRuleSet() {
        val exception = assertThrows(RuleSetValidationException::class.java) {
            EmbeddedRuleSetParser.parse(validRuleSetJson().replace("\"enabled\": true", "\"enabled\": \"true\""))
        }

        assertTrue(exception.message.orEmpty().contains("boolean 'enabled'"))
    }

    @Test
    fun parse_unsupportedSeverity_rejectsRuleSet() {
        val exception = assertThrows(RuleSetValidationException::class.java) {
            EmbeddedRuleSetParser.parse(validRuleSetJson().replace("MANDATORY", "WARNING"))
        }

        assertTrue(exception.message.orEmpty().contains("unsupported 'severity'"))
    }

    @Test
    fun parse_duplicateRuleIdentifiers_rejectsRuleSet() {
        val duplicatedRule = validRuleObject().replace("YD_TRCY_001", "YD_TRCY_002")
        val json = validRuleSetJson().replace(validRuleObject(), "$duplicatedRule,$duplicatedRule")

        val exception = assertThrows(RuleSetValidationException::class.java) {
            EmbeddedRuleSetParser.parse(json)
        }

        assertTrue(exception.message.orEmpty().contains("Duplicate rule id"))
    }

    @Test
    fun parse_unknownRuleSource_rejectsRuleSet() {
        val exception = assertThrows(RuleSetValidationException::class.java) {
            EmbeddedRuleSetParser.parse(
                validRuleSetJson().replace(
                    "\"sourceId\": \"baseline-20260526\"",
                    "\"sourceId\": \"missing-source\"",
                ),
            )
        }

        assertTrue(exception.message.orEmpty().contains("unknown sourceId"))
    }

    @Test
    fun parse_targetTableMissingFromRequiredTables_rejectsRuleSet() {
        val exception = assertThrows(RuleSetValidationException::class.java) {
            EmbeddedRuleSetParser.parse(validRuleSetJson().replace("[\"YD_TRCY_PT\"]", "[\"YX_TRCY_TB\"]"))
        }

        assertTrue(exception.message.orEmpty().contains("must be included in requiredTables"))
    }

    private fun validRuleSetJson(): String =
        """
        {
          "schemaVersion": 1,
          "ruleSetVersion": "2026.05-initial",
          "publishedAt": "2026-05-26",
          "sources": [
            {
              "id": "baseline-20260526",
              "kind": "BASE_SNAPSHOT",
              "label": "Enabled rules snapshot",
              "description": "Rules extracted from the verified ZDB sample."
            }
          ],
          "rules": [${validRuleObject()}]
        }
        """.trimIndent()

    private fun validRuleObject(): String =
        """
        {
          "id": "YD_TRCY_001",
          "sourceId": "baseline-20260526",
          "enabled": true,
          "severity": "MANDATORY",
          "targetTable": "YD_TRCY_PT",
          "title": "Missing investigator",
          "explanation": "The investigator is required.",
          "requiredTables": ["YD_TRCY_PT"],
          "requiredFields": ["YD_ID", "DC_RY", "XIAN"],
          "locatorFields": ["XIAN", "YD_ID"],
          "sql": "SELECT XIAN, YD_ID, DC_RY AS actualValue FROM YD_TRCY_PT WHERE YD_ID = :ydId"
        }
        """.trimIndent()
}
