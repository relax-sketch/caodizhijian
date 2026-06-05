package com.example.myapplication.quality.rules

import java.io.File
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionalRuleFixtureTest {
    @Test
    fun additionalRules_matchBadFixtureAndPassGoodFixture() {
        val expected = expectedResult()
        openFixture().use { connection ->
            val badMatches = matchingRules(connection, expected.getString("badPlotId"))
            val expectedBadMatches = expected.getJSONArray("badPlotExpectedAdditionalRuleIds").toStringList()
            val seededRules = addedRules().filter { it.id in expectedBadMatches }
            assertTrue(
                "Expected seeded bad plot to still match $expectedBadMatches, got $badMatches.",
                badMatches.containsAll(expectedBadMatches),
            )
            assertEquals(
                expected.getJSONArray("goodPlotExpectedAdditionalRuleIds").toStringList(),
                matchingRules(connection, expected.getString("goodPlotId"), seededRules),
            )
        }
    }

    @Test
    fun relatedLineRule_doesNotReturnRecordBelongingToAnotherPlot() {
        val expected = expectedResult()
        val rule = addedRules().single { it.id == "ADD_GRASS_029" }
        openFixture().use { connection ->
            assertTrue(matches(connection, rule, expected.getString("badPlotId")))
            assertFalse(matches(connection, rule, expected.getString("goodPlotId")))
        }
    }

    @Test
    fun transectEndpointDistanceRule_acceptsTwentyMetersWithinHalfMeter() {
        val rule = addedRules().single { it.id == "ADD_GRASS_039" }
        openMutableFixture().use { connection ->
            clearTables(connection, "YD_TRCY_PT", "YX_TRCY_TB")
            insertPlot(connection, 910001, "DISTANCE_OK", "distance-ok-guid", 1000.0, 1000.0)
            insertTransect(connection, 910011, "DISTANCE_OK", "distance-ok-guid", 1020.0, 1000.0)
            insertPlot(connection, 910002, "DISTANCE_BAD", "distance-bad-guid", 1000.0, 1000.0)
            insertTransect(connection, 910012, "DISTANCE_BAD", "distance-bad-guid", 1021.0, 1000.0)

            assertFalse(matches(connection, rule, "DISTANCE_OK"))
            assertTrue(matches(connection, rule, "DISTANCE_BAD"))
        }
    }

    @Test
    fun transectPhotoRule_acceptsTwoPhotosAndRejectsOnePhoto() {
        val rule = addedRules().single { it.id == "ADD_GRASS_038" }
        openMutableFixture().use { connection ->
            clearTables(connection, "YX_TRCY_TB", "FS_DOCUMENT")
            insertTransect(connection, 915001, "TRANSECT_PHOTO_OK", "photo-ok-parent-guid", 1020.0, 1000.0)
            insertDocument(connection, 915011, "YX_TRCY_TB", "TRANSECT_PHOTO_OK-line-guid")
            insertDocument(connection, 915012, "YX_TRCY_TB", "TRANSECT_PHOTO_OK-line-guid")
            insertTransect(connection, 915002, "TRANSECT_PHOTO_BAD", "photo-bad-parent-guid", 1020.0, 1000.0)
            insertDocument(connection, 915021, "YX_TRCY_TB", "TRANSECT_PHOTO_BAD-line-guid")

            assertFalse(matches(connection, rule, "TRANSECT_PHOTO_OK"))
            assertTrue(matches(connection, rule, "TRANSECT_PHOTO_BAD"))
        }
    }

    @Test
    fun slopeAspectRule_allowsFlatlandOrNoSlopeBelowFiveDegrees() {
        val rule = addedRules().single { it.id == "ADD_GRASS_023" }
        openMutableFixture().use { connection ->
            clearTables(connection, "YD_TRCY_PT")
            insertNaturalPlotAttributes(connection, 916001, "SLOPE_FLAT_OK", slope = 3.0, slopePosition = "6", aspect = "9")
            insertNaturalPlotAttributes(connection, 916002, "SLOPE_NO_SLOPE_OK", slope = 3.0, slopePosition = "0", aspect = "9")
            insertNaturalPlotAttributes(connection, 916003, "SLOPE_LOW_BAD", slope = 3.0, slopePosition = "1", aspect = "9")
            insertNaturalPlotAttributes(connection, 916004, "SLOPE_ASPECT_BAD", slope = 3.0, slopePosition = "6", aspect = "1")
            insertNaturalPlotAttributes(connection, 916005, "SLOPE_HIGH_OK", slope = 8.0, slopePosition = "1", aspect = "2")
            insertNaturalPlotAttributes(connection, 916006, "SLOPE_HIGH_BAD", slope = 8.0, slopePosition = "6", aspect = "9")

            assertFalse(matches(connection, rule, "SLOPE_FLAT_OK"))
            assertFalse(matches(connection, rule, "SLOPE_NO_SLOPE_OK"))
            assertTrue(matches(connection, rule, "SLOPE_LOW_BAD"))
            assertTrue(matches(connection, rule, "SLOPE_ASPECT_BAD"))
            assertFalse(matches(connection, rule, "SLOPE_HIGH_OK"))
            assertTrue(matches(connection, rule, "SLOPE_HIGH_BAD"))
        }
    }

    @Test
    fun erosionRule_requiresDegreeBlankWhenErosionTypeIsNone() {
        val rule = addedRules().single { it.id == "ADD_GRASS_024" }
        openMutableFixture().use { connection ->
            clearTables(connection, "YD_TRCY_PT")
            insertNaturalPlotAttributes(connection, 917001, "EROSION_NONE_EMPTY_OK", erosionType = "5", erosionDegree = "")
            insertNaturalPlotAttributes(connection, 917002, "EROSION_NONE_NULL_OK", erosionType = "5", erosionDegree = null)
            insertNaturalPlotAttributes(connection, 917003, "EROSION_NONE_FILLED_BAD", erosionType = "5", erosionDegree = "0")
            insertNaturalPlotAttributes(connection, 917004, "EROSION_USED_FILLED_OK", erosionType = "1", erosionDegree = "1")
            insertNaturalPlotAttributes(connection, 917005, "EROSION_USED_EMPTY_BAD", erosionType = "1", erosionDegree = "")
            insertNaturalPlotAttributes(connection, 917006, "EROSION_USED_NONE_BAD", erosionType = "1", erosionDegree = "0")

            assertFalse(matches(connection, rule, "EROSION_NONE_EMPTY_OK"))
            assertFalse(matches(connection, rule, "EROSION_NONE_NULL_OK"))
            assertTrue(matches(connection, rule, "EROSION_NONE_FILLED_BAD"))
            assertFalse(matches(connection, rule, "EROSION_USED_FILLED_OK"))
            assertTrue(matches(connection, rule, "EROSION_USED_EMPTY_BAD"))
            assertTrue(matches(connection, rule, "EROSION_USED_NONE_BAD"))
        }
    }

    @Test
    fun utilizationRule_treatsOtherAsUnusedAndConcreteModesAsUsed() {
        val rule = addedRules().single { it.id == "ADD_GRASS_025" }
        openMutableFixture().use { connection ->
            clearTables(connection, "YD_TRCY_PT")
            insertNaturalPlotAttributes(connection, 918001, "UTIL_OTHER_UNUSED_OK", useMode = "36", useIntensity = "9")
            insertNaturalPlotAttributes(connection, 918002, "UTIL_OTHER_USED_BAD", useMode = "36", useIntensity = "1")
            insertNaturalPlotAttributes(connection, 918003, "UTIL_USED_LIGHT_OK", useMode = "11", useIntensity = "1")
            insertNaturalPlotAttributes(connection, 918004, "UTIL_USED_UNUSED_BAD", useMode = "11", useIntensity = "9")

            assertFalse(matches(connection, rule, "UTIL_OTHER_UNUSED_OK"))
            assertTrue(matches(connection, rule, "UTIL_OTHER_USED_BAD"))
            assertFalse(matches(connection, rule, "UTIL_USED_LIGHT_OK"))
            assertTrue(matches(connection, rule, "UTIL_USED_UNUSED_BAD"))
        }
    }

    @Test
    fun measurementAverageHeightRule_requiresAverageToLeanTowardHigherCoverageSide() {
        val rule = addedRules().single { it.id == "ADD_GRASS_046" }
        openMutableFixture().use { connection ->
            clearTables(connection, "YF_TRCYCC_TB", "ZWDCB_CCYF_TB")
            insertMeasurementSample(connection, 920001, "MEASURE_HEIGHT_OK", 25.0, "measure-height-ok-guid")
            insertMeasurementPlant(connection, 920011, "MEASURE_HEIGHT_OK", 1, "measure-height-ok-guid", 20.0, 80.0)
            insertMeasurementPlant(connection, 920012, "MEASURE_HEIGHT_OK", 2, "measure-height-ok-guid", 40.0, 20.0)
            insertMeasurementSample(connection, 920002, "MEASURE_HEIGHT_BAD", 35.0, "measure-height-bad-guid")
            insertMeasurementPlant(connection, 920021, "MEASURE_HEIGHT_BAD", 1, "measure-height-bad-guid", 20.0, 80.0)
            insertMeasurementPlant(connection, 920022, "MEASURE_HEIGHT_BAD", 2, "measure-height-bad-guid", 40.0, 20.0)

            assertFalse(matches(connection, rule, "MEASURE_HEIGHT_OK"))
            assertTrue(matches(connection, rule, "MEASURE_HEIGHT_BAD"))
        }
    }

    @Test
    fun observationAverageHeightRule_requiresAverageToLeanTowardHigherCoverageSide() {
        val rule = addedRules().single { it.id == "ADD_GRASS_060" }
        openMutableFixture().use { connection ->
            clearTables(connection, "YF_TRCYGC_TB", "ZWDCB_GCYF_TB")
            insertObservationSample(connection, 930001, "OBS_HEIGHT_OK", 25.0, "obs-height-ok-guid")
            insertObservationPlant(connection, 930011, "OBS_HEIGHT_OK", 1, "obs-height-ok-guid", 20.0, 80.0)
            insertObservationPlant(connection, 930012, "OBS_HEIGHT_OK", 2, "obs-height-ok-guid", 40.0, 20.0)
            insertObservationSample(connection, 930002, "OBS_HEIGHT_BAD", 35.0, "obs-height-bad-guid")
            insertObservationPlant(connection, 930021, "OBS_HEIGHT_BAD", 1, "obs-height-bad-guid", 20.0, 80.0)
            insertObservationPlant(connection, 930022, "OBS_HEIGHT_BAD", 2, "obs-height-bad-guid", 40.0, 20.0)

            assertFalse(matches(connection, rule, "OBS_HEIGHT_OK"))
            assertTrue(matches(connection, rule, "OBS_HEIGHT_BAD"))
        }
    }

    @Test
    fun sampleTotalCoverageRules_compareAgainstPlantCoverageSum() {
        val measurementRule = addedRules().single { it.id == "ADD_GRASS_047" }
        val observationRule = addedRules().single { it.id == "ADD_GRASS_061" }
        openMutableFixture().use { connection ->
            clearTables(connection, "YF_TRCYCC_TB", "ZWDCB_CCYF_TB", "YF_TRCYGC_TB", "ZWDCB_GCYF_TB")
            insertMeasurementSample(connection, 935001, "MEASURE_COVER_SUM_OK", 25.0, "measure-cover-ok-guid", totalCoverage = 80.0)
            insertMeasurementPlant(connection, 935011, "MEASURE_COVER_SUM_OK", 1, "measure-cover-ok-guid", coverage = 20.0)
            insertMeasurementPlant(connection, 935012, "MEASURE_COVER_SUM_OK", 2, "measure-cover-ok-guid", coverage = 65.0)
            insertMeasurementSample(connection, 935002, "MEASURE_COVER_SUM_BAD", 25.0, "measure-cover-bad-guid", totalCoverage = 90.0)
            insertMeasurementPlant(connection, 935021, "MEASURE_COVER_SUM_BAD", 1, "measure-cover-bad-guid", coverage = 20.0)
            insertMeasurementPlant(connection, 935022, "MEASURE_COVER_SUM_BAD", 2, "measure-cover-bad-guid", coverage = 65.0)

            insertObservationSample(connection, 935101, "OBS_COVER_SUM_OK", 25.0, "obs-cover-ok-guid", totalCoverage = 80.0)
            insertObservationPlant(connection, 935111, "OBS_COVER_SUM_OK", 1, "obs-cover-ok-guid", coverage = 20.0)
            insertObservationPlant(connection, 935112, "OBS_COVER_SUM_OK", 2, "obs-cover-ok-guid", coverage = 65.0)
            insertObservationSample(connection, 935102, "OBS_COVER_SUM_BAD", 25.0, "obs-cover-bad-guid", totalCoverage = 90.0)
            insertObservationPlant(connection, 935121, "OBS_COVER_SUM_BAD", 1, "obs-cover-bad-guid", coverage = 20.0)
            insertObservationPlant(connection, 935122, "OBS_COVER_SUM_BAD", 2, "obs-cover-bad-guid", coverage = 65.0)

            assertFalse(matches(connection, measurementRule, "MEASURE_COVER_SUM_OK"))
            assertTrue(matches(connection, measurementRule, "MEASURE_COVER_SUM_BAD"))
            assertFalse(matches(connection, observationRule, "OBS_COVER_SUM_OK"))
            assertTrue(matches(connection, observationRule, "OBS_COVER_SUM_BAD"))
        }
    }

    @Test
    fun sampleHeightRules_doNotDeclarePlantFieldsAsTargetRequiredFields() {
        listOf("ADD_GRASS_046", "ADD_GRASS_060", "ADD_GRASS_070", "ADD_GRASS_071").forEach { ruleId ->
            val rule = addedRules().single { it.id == ruleId }

            assertFalse("$ruleId should not require plant height on ${rule.targetTable}.", "H" in rule.requiredFields)
            assertFalse("$ruleId should not require plant coverage on ${rule.targetTable}.", "FVC" in rule.requiredFields)
        }
    }

    @Test
    fun weightedRecommendedHeightRules_useCoveragePercentAsAdvisoryReference() {
        val measurementRule = addedRules().firstOrNull { it.id == "ADD_GRASS_070" }
        val observationRule = addedRules().firstOrNull { it.id == "ADD_GRASS_071" }
        assertNotNull(measurementRule)
        assertNotNull(observationRule)

        openMutableFixture().use { connection ->
            clearTables(connection, "YF_TRCYCC_TB", "ZWDCB_CCYF_TB", "YF_TRCYGC_TB", "ZWDCB_GCYF_TB")
            insertMeasurementSample(connection, 940001, "MEASURE_RECOMMENDED_OK", 24.4, "measure-recommended-ok-guid")
            insertMeasurementPlant(connection, 940011, "MEASURE_RECOMMENDED_OK", 1, "measure-recommended-ok-guid", 20.0, 80.0)
            insertMeasurementPlant(connection, 940012, "MEASURE_RECOMMENDED_OK", 2, "measure-recommended-ok-guid", 40.0, 20.0)
            insertMeasurementSample(connection, 940002, "MEASURE_RECOMMENDED_BAD", 25.0, "measure-recommended-bad-guid")
            insertMeasurementPlant(connection, 940021, "MEASURE_RECOMMENDED_BAD", 1, "measure-recommended-bad-guid", 20.0, 80.0)
            insertMeasurementPlant(connection, 940022, "MEASURE_RECOMMENDED_BAD", 2, "measure-recommended-bad-guid", 40.0, 20.0)

            insertObservationSample(connection, 940101, "OBS_RECOMMENDED_OK", 24.4, "obs-recommended-ok-guid")
            insertObservationPlant(connection, 940111, "OBS_RECOMMENDED_OK", 1, "obs-recommended-ok-guid", 20.0, 80.0)
            insertObservationPlant(connection, 940112, "OBS_RECOMMENDED_OK", 2, "obs-recommended-ok-guid", 40.0, 20.0)
            insertObservationSample(connection, 940102, "OBS_RECOMMENDED_BAD", 25.0, "obs-recommended-bad-guid")
            insertObservationPlant(connection, 940121, "OBS_RECOMMENDED_BAD", 1, "obs-recommended-bad-guid", 20.0, 80.0)
            insertObservationPlant(connection, 940122, "OBS_RECOMMENDED_BAD", 2, "obs-recommended-bad-guid", 40.0, 20.0)

            assertFalse(matches(connection, measurementRule!!, "MEASURE_RECOMMENDED_OK"))
            assertTrue(matches(connection, measurementRule, "MEASURE_RECOMMENDED_BAD"))
            assertFalse(matches(connection, observationRule!!, "OBS_RECOMMENDED_OK"))
            assertTrue(matches(connection, observationRule, "OBS_RECOMMENDED_BAD"))
        }
    }

    @Test
    fun measurementDominantPlantRule_splitsSeparatorsAndIgnoresJuPrefixInPlotDominants() {
        val rule = addedRules().single { it.id == "ADD_GRASS_053" }
        openMutableFixture().use { connection ->
            clearTables(connection, "YD_TRCY_PT", "ZWDCB_CCYF_TB")
            insertNaturalPlotAttributes(connection, 945001, "DOMINANT_COMMA_OK", dominantPlants = "具木蓝的白茅、金鸡菊")
            insertMeasurementPlant(
                connection,
                945011,
                "DOMINANT_COMMA_OK",
                1,
                "dominant-comma-ok-guid",
                name = "白茅,鸡眼草",
                youshizhong = "1",
            )
            insertNaturalPlotAttributes(connection, 945002, "DOMINANT_CHINESE_COMMA_OK", dominantPlants = "具木蓝的白茅，金鸡菊")
            insertMeasurementPlant(
                connection,
                945021,
                "DOMINANT_CHINESE_COMMA_OK",
                1,
                "dominant-chinese-comma-ok-guid",
                name = "狗尾草，金鸡菊",
                youshizhong = "1",
            )
            insertNaturalPlotAttributes(connection, 945003, "DOMINANT_DUNHAO_OK", dominantPlants = "具木蓝的白茅、金鸡菊")
            insertMeasurementPlant(
                connection,
                945031,
                "DOMINANT_DUNHAO_OK",
                1,
                "dominant-dunhao-ok-guid",
                name = "狗尾草、金鸡菊",
                youshizhong = "1",
            )
            insertNaturalPlotAttributes(connection, 945004, "DOMINANT_BAD", dominantPlants = "具木蓝的白茅、金鸡菊")
            insertMeasurementPlant(
                connection,
                945041,
                "DOMINANT_BAD",
                1,
                "dominant-bad-guid",
                name = "狗尾草、鸡眼草",
                youshizhong = "1",
            )

            assertFalse(matches(connection, rule, "DOMINANT_COMMA_OK"))
            assertFalse(matches(connection, rule, "DOMINANT_CHINESE_COMMA_OK"))
            assertFalse(matches(connection, rule, "DOMINANT_DUNHAO_OK"))
            assertTrue(matches(connection, rule, "DOMINANT_BAD"))
        }
    }

    @Test
    fun measurementPlantCategoryRule_allowsUniqueCategoriesAndRejectsDuplicates() {
        val rule = addedRules().single { it.id == "ADD_GRASS_050" }
        openMutableFixture().use { connection ->
            clearTables(connection, "ZWDCB_CCYF_TB")
            insertMeasurementPlant(
                connection,
                950001,
                "MEASURE_CATEGORY_OK",
                1,
                "measure-category-ok-guid",
                youshizhong = "1",
                keshi = "1",
                duhai = "2",
            )
            insertMeasurementPlant(
                connection,
                950002,
                "MEASURE_CATEGORY_OK",
                2,
                "measure-category-ok-guid",
                youshizhong = "1",
                keshi = "2",
                duhai = "1",
            )
            insertMeasurementPlant(
                connection,
                950011,
                "MEASURE_CATEGORY_BAD",
                1,
                "measure-category-bad-guid",
                youshizhong = "2",
                keshi = "1",
                duhai = "2",
            )
            insertMeasurementPlant(
                connection,
                950012,
                "MEASURE_CATEGORY_BAD",
                2,
                "measure-category-bad-guid",
                youshizhong = "2",
                keshi = "1",
                duhai = "2",
            )

            assertFalse(matches(connection, rule, "MEASURE_CATEGORY_OK"))
            assertTrue(matches(connection, rule, "MEASURE_CATEGORY_BAD"))
        }
    }

    @Test
    fun observationPlantCategoryRule_allowsUniqueCategoriesAndRejectsDuplicates() {
        val rule = addedRules().single { it.id == "ADD_GRASS_064" }
        openMutableFixture().use { connection ->
            clearTables(connection, "ZWDCB_GCYF_TB")
            insertObservationPlant(
                connection,
                960001,
                "OBS_CATEGORY_OK",
                1,
                "obs-category-ok-guid",
                youshizhong = "1",
                keshi = "1",
                duhai = "2",
            )
            insertObservationPlant(
                connection,
                960002,
                "OBS_CATEGORY_OK",
                2,
                "obs-category-ok-guid",
                youshizhong = "1",
                keshi = "2",
                duhai = "1",
            )
            insertObservationPlant(
                connection,
                960011,
                "OBS_CATEGORY_BAD",
                1,
                "obs-category-bad-guid",
                youshizhong = "2",
                keshi = "1",
                duhai = "2",
            )
            insertObservationPlant(
                connection,
                960012,
                "OBS_CATEGORY_BAD",
                2,
                "obs-category-bad-guid",
                youshizhong = "2",
                keshi = "1",
                duhai = "2",
            )

            assertFalse(matches(connection, rule, "OBS_CATEGORY_OK"))
            assertTrue(matches(connection, rule, "OBS_CATEGORY_BAD"))
        }
    }

    @Test
    fun allConvertedSpreadsheetRules_compileAgainstFixture() {
        openFixture().use { connection ->
            addedRules().forEach { rule ->
                connection.prepareStatement("EXPLAIN QUERY PLAN ${rule.sql.replace(":ydId", "?")}").use { statement ->
                    statement.setString(1, "FIX_BAD_001")
                    statement.executeQuery().close()
                }
            }
        }
    }

    private fun matchingRules(
        connection: Connection,
        plotId: String,
        rules: List<EmbeddedRule> = addedRules(),
    ): List<String> =
        rules.filter { matches(connection, it, plotId) }.map(EmbeddedRule::id)

    private fun matches(connection: Connection, rule: EmbeddedRule, plotId: String): Boolean =
        connection.prepareStatement(rule.sql.replace(":ydId", "?")).use { statement ->
            statement.setString(1, plotId)
            statement.executeQuery().use { results -> results.next() }
        }

    private fun addedRules(): List<EmbeddedRule> =
        EmbeddedRuleSetParser.parse(assetRuleSet().readText()).rules
            .filter { it.sourceId == "grassland-additional-20260526" }

    private fun openFixture(): Connection {
        Class.forName("org.sqlite.JDBC")
        val copiedFixture = Files.createTempFile("grassland-quality-check-", ".zdb")
        javaClass.getResourceAsStream("/fixtures/grassland-quality-check-fixture.zdb").use { source ->
            requireNotNull(source) { "Fixture resource is missing." }
            Files.copy(source, copiedFixture, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return DriverManager.getConnection("jdbc:sqlite:$copiedFixture").also { connection ->
            connection.createStatement().use { it.execute("PRAGMA query_only=ON") }
        }
    }

    private fun openMutableFixture(): Connection {
        Class.forName("org.sqlite.JDBC")
        val copiedFixture = Files.createTempFile("grassland-quality-check-mutable-", ".zdb")
        javaClass.getResourceAsStream("/fixtures/grassland-quality-check-fixture.zdb").use { source ->
            requireNotNull(source) { "Fixture resource is missing." }
            Files.copy(source, copiedFixture, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        return DriverManager.getConnection("jdbc:sqlite:$copiedFixture")
    }

    private fun clearTables(connection: Connection, vararg tables: String) {
        connection.createStatement().use { statement ->
            tables.forEach { table ->
                statement.executeUpdate("DELETE FROM \"$table\"")
            }
        }
    }

    private fun insertPlot(
        connection: Connection,
        pkUid: Int,
        plotId: String,
        guid: String,
        x: Double,
        y: Double,
    ) {
        connection.prepareStatement(
            """INSERT INTO "YD_TRCY_PT" ("PK_UID", "YD_ID", "MZGUID", "ZUOBIAO_X", "ZUOBIAO_Y") VALUES (?, ?, ?, ?, ?)""",
        ).use { statement ->
            statement.setInt(1, pkUid)
            statement.setString(2, plotId)
            statement.setString(3, guid)
            statement.setDouble(4, x)
            statement.setDouble(5, y)
            statement.executeUpdate()
        }
    }

    private fun insertTransect(
        connection: Connection,
        pkUid: Int,
        plotId: String,
        parentGuid: String,
        endpointX: Double,
        endpointY: Double,
    ) {
        connection.prepareStatement(
            """INSERT INTO "YX_TRCY_TB" ("PK_UID", "YD_ID", "YX_ID", "YX_CD", "MZGUID", "XB_GLH", "YX_X", "YX_Y") VALUES (?, ?, 1, 20, ?, ?, ?, ?)""",
        ).use { statement ->
            statement.setInt(1, pkUid)
            statement.setString(2, plotId)
            statement.setString(3, "$plotId-line-guid")
            statement.setString(4, parentGuid)
            statement.setDouble(5, endpointX)
            statement.setDouble(6, endpointY)
            statement.executeUpdate()
        }
    }

    private fun insertDocument(
        connection: Connection,
        pkUid: Int,
        tableName: String,
        parentGuid: String,
    ) {
        connection.prepareStatement(
            """INSERT INTO "FS_DOCUMENT" ("PK_UID", "main_body_table_id", "main_body_guid", "adjunct_path") VALUES (?, ?, ?, ?)""",
        ).use { statement ->
            statement.setInt(1, pkUid)
            statement.setString(2, tableName)
            statement.setString(3, parentGuid)
            statement.setString(4, "/photos/$parentGuid-$pkUid.jpg")
            statement.executeUpdate()
        }
    }

    private fun insertNaturalPlotAttributes(
        connection: Connection,
        pkUid: Int,
        plotId: String,
        slope: Double? = null,
        slopePosition: String? = null,
        aspect: String? = null,
        erosionType: String? = null,
        erosionDegree: String? = null,
        useMode: String? = null,
        useIntensity: String? = null,
        dominantPlants: String? = null,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO "YD_TRCY_PT"
            ("PK_UID", "YD_ID", "MZGUID", "PO_DU", "PO_WEI", "PO_XIANG", "DB_QS_LX", "DB_QS_CD", "LYFS", "LYQD", "YS_CZ")
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, pkUid)
            statement.setString(2, plotId)
            statement.setString(3, "$plotId-plot-guid")
            if (slope == null) {
                statement.setNull(4, java.sql.Types.DOUBLE)
            } else {
                statement.setDouble(4, slope)
            }
            statement.setString(5, slopePosition)
            statement.setString(6, aspect)
            statement.setString(7, erosionType)
            statement.setString(8, erosionDegree)
            statement.setString(9, useMode)
            statement.setString(10, useIntensity)
            statement.setString(11, dominantPlants)
            statement.executeUpdate()
        }
    }

    private fun insertMeasurementSample(
        connection: Connection,
        pkUid: Int,
        plotId: String,
        averageHeight: Double,
        sampleGuid: String,
        totalCoverage: Double = 100.0,
    ) {
        connection.prepareStatement(
            """INSERT INTO "YF_TRCYCC_TB" ("PK_UID", "YD_ID", "YF_ID", "MZGUID", "CQPJ_GD", "ZGD") VALUES (?, ?, 1, ?, ?, ?)""",
        ).use { statement ->
            statement.setInt(1, pkUid)
            statement.setString(2, plotId)
            statement.setString(3, sampleGuid)
            statement.setDouble(4, averageHeight)
            statement.setDouble(5, totalCoverage)
            statement.executeUpdate()
        }
    }

    private fun insertObservationSample(
        connection: Connection,
        pkUid: Int,
        plotId: String,
        averageHeight: Double,
        sampleGuid: String,
        totalCoverage: Double = 100.0,
    ) {
        connection.prepareStatement(
            """INSERT INTO "YF_TRCYGC_TB" ("PK_UID", "YD_ID", "YF_ID", "MZGUID", "CQPJ_GD", "ZGD") VALUES (?, ?, 1, ?, ?, ?)""",
        ).use { statement ->
            statement.setInt(1, pkUid)
            statement.setString(2, plotId)
            statement.setString(3, sampleGuid)
            statement.setDouble(4, averageHeight)
            statement.setDouble(5, totalCoverage)
            statement.executeUpdate()
        }
    }

    private fun insertMeasurementPlant(
        connection: Connection,
        pkUid: Int,
        plotId: String,
        plantIndex: Int,
        sampleGuid: String,
        height: Double = 20.0,
        coverage: Double = 50.0,
        name: String = "plant-$plantIndex",
        youshizhong: String = "1",
        keshi: String = "1",
        duhai: String = "2",
    ) {
        connection.prepareStatement(
            """INSERT INTO "ZWDCB_CCYF_TB" ("PK_UID", "YD_ID", "YF_ID", "XB_GLH", "ZW_MC", "H", "FVC", "YOUSHIZHONG", "KESHI", "DUHAI", "MZGUID") VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?)""",
        ).use { statement ->
            statement.setInt(1, pkUid)
            statement.setString(2, plotId)
            statement.setString(3, sampleGuid)
            statement.setString(4, name)
            statement.setDouble(5, height)
            statement.setDouble(6, coverage)
            statement.setString(7, youshizhong)
            statement.setString(8, keshi)
            statement.setString(9, duhai)
            statement.setString(10, "$plotId-measure-plant-$plantIndex-guid")
            statement.executeUpdate()
        }
    }

    private fun insertObservationPlant(
        connection: Connection,
        pkUid: Int,
        plotId: String,
        plantIndex: Int,
        sampleGuid: String,
        height: Double = 20.0,
        coverage: Double = 50.0,
        youshizhong: String = "1",
        keshi: String = "1",
        duhai: String = "2",
    ) {
        connection.prepareStatement(
            """INSERT INTO "ZWDCB_GCYF_TB" ("PK_UID", "YD_ID", "YF_ID", "XB_GLH", "ZW_MC", "H", "FVC", "YOUSHIZHONG", "KESHI", "DUHAI", "MZGUID") VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?)""",
        ).use { statement ->
            statement.setInt(1, pkUid)
            statement.setString(2, plotId)
            statement.setString(3, sampleGuid)
            statement.setString(4, "plant-$plantIndex")
            statement.setDouble(5, height)
            statement.setDouble(6, coverage)
            statement.setString(7, youshizhong)
            statement.setString(8, keshi)
            statement.setString(9, duhai)
            statement.setString(10, "$plotId-observation-plant-$plantIndex-guid")
            statement.executeUpdate()
        }
    }

    private fun expectedResult(): JSONObject =
        javaClass.getResourceAsStream("/fixtures/grassland-quality-check-expected.json").use { source ->
            requireNotNull(source) { "Expected fixture output is missing." }
            JSONObject(source.bufferedReader(Charsets.UTF_8).readText())
        }

    private fun assetRuleSet(): File =
        listOf(
            File("src/main/assets/rules/rule-set.json"),
            File("app/src/main/assets/rules/rule-set.json"),
        ).first(File::isFile)

    private fun org.json.JSONArray.toStringList(): List<String> =
        (0 until length()).map(::getString)
}
