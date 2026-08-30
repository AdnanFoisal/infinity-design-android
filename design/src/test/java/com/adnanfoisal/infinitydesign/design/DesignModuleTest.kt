package com.adnanfoisal.infinitydesign.design

import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.design.commands.DesignCommand
import com.adnanfoisal.infinitydesign.design.dsl.Bounds
import com.adnanfoisal.infinitydesign.design.dsl.CanvasSpec
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.design.dsl.DesignElement
import com.adnanfoisal.infinitydesign.design.dsl.ShapeKind
import com.adnanfoisal.infinitydesign.design.dsl.BackgroundSpec
import com.adnanfoisal.infinitydesign.design.dsl.DocumentMetadata
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocumentCodec
import com.adnanfoisal.infinitydesign.design.dsl.PaletteSpec
import com.adnanfoisal.infinitydesign.design.dsl.TypographySpec
import com.adnanfoisal.infinitydesign.design.history.DesignHistory
import com.adnanfoisal.infinitydesign.design.validation.DesignDocumentMigrator
import com.adnanfoisal.infinitydesign.design.validation.DesignValidator
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DesignDocumentCodecTest {

    @Test fun `round trip preserves document`() {
        val doc = sampleDoc()
        val s = DesignDocumentCodec.encode(doc)
        val back = DesignDocumentCodec.decode(s)
        assertThat(back.isOk).isTrue()
        val decoded = (back as AppResult.Ok).value
        assertThat(decoded.id).isEqualTo(doc.id)
        assertThat(decoded.elements.size).isEqualTo(doc.elements.size)
        // Same content again should produce identical JSON (stable serialization).
        assertThat(DesignDocumentCodec.encode(decoded)).isEqualTo(s)
    }

    @Test fun `decode rejects empty input`() {
        val r = DesignDocumentCodec.decode("")
        assertThat(r.isErr).isTrue()
    }

    @Test fun `decode rejects malformed json`() {
        val r = DesignDocumentCodec.decode("not json")
        assertThat(r.isErr).isTrue()
    }

    @Test fun `forward compatible unknown keys`() {
        val doc = sampleDoc()
        val s = DesignDocumentCodec.encode(doc).replace(
            "\"schemaVersion\"", "\"futureFlag\":\"x\",\"schemaVersion\""
        )
        val r = DesignDocumentCodec.decode(s)
        assertThat(r.isOk).isTrue()
    }
}

class DesignValidatorTest {

    @Test fun `valid document passes`() {
        val r = DesignValidator.validate(sampleDoc())
        assertThat(r.isOk).isTrue()
    }

    @Test fun `rejects NaN dimensions`() {
        val doc = sampleDoc().copy(
            canvas = CanvasSpec(Float.NaN, 100f),
        )
        val r = DesignValidator.validate(doc)
        assertThat(r.isErr).isTrue()
    }

    @Test fun `rejects bad color`() {
        val doc = sampleDoc().copy(
            background = BackgroundSpec.Solid("not-a-color"),
        )
        assertThat(DesignValidator.validate(doc).isErr).isTrue()
    }

    @Test fun `rejects unknown effect`() {
        val el = DesignElement.Procedural(
            id = "p1", bounds = Bounds(0f, 0f, 10f, 10f),
            effect = "evil-effect",
        )
        val doc = sampleDoc().copy(elements = listOf(el))
        assertThat(DesignValidator.validate(doc).isErr).isTrue()
    }

    @Test fun `rejects duplicate ids`() {
        val e = DesignElement.Shape(
            id = "x", bounds = Bounds(0f, 0f, 10f, 10f), kind = ShapeKind.RECTANGLE
        )
        val doc = sampleDoc().copy(elements = listOf(e, e))
        assertThat(DesignValidator.validate(doc).isErr).isTrue()
    }

    @Test fun `allows valid procedural effects`() {
        for (effect in listOf("cloud", "grain", "halftone", "particle_field")) {
            val el = DesignElement.Procedural(
                id = "p", bounds = Bounds(0f, 0f, 10f, 10f), effect = effect,
            )
            val doc = sampleDoc().copy(elements = listOf(el))
            assertThat(DesignValidator.validate(doc).isOk).isTrue()
        }
    }
}

class MigratorTest {

    @Test fun `latest version is 1`() {
        assertThat(DesignDocumentMigrator.latestVersion()).isEqualTo(1)
    }

    @Test fun `migrate latest is no-op`() {
        val doc = sampleDoc()
        val r = DesignDocumentMigrator.migrate(doc)
        assertThat(r.isOk).isTrue()
        assertThat((r as AppResult.Ok).value.schemaVersion).isEqualTo(1)
    }

    @Test fun `rejects future version`() {
        val doc = sampleDoc().copy(schemaVersion = 999)
        assertThat(DesignDocumentMigrator.migrate(doc).isErr).isTrue()
    }
}

class HistoryTest {

    @Test fun `move then undo restores bounds`() {
        val el = DesignElement.Shape(
            id = "e1", bounds = Bounds(10f, 10f, 100f, 100f), kind = ShapeKind.RECTANGLE
        )
        val doc = sampleDoc(elements = listOf(el))
        val h = DesignHistory(doc)
        h.push(DesignCommand.MoveElement("c1", "e1", 5f, 7f))
        val after = h.state.value.elements.first()
        assertThat(after.bounds.x).isEqualTo(15f)
        assertThat(after.bounds.y).isEqualTo(17f)
        h.undo()
        val restored = h.state.value.elements.first()
        assertThat(restored.bounds.x).isEqualTo(10f)
        assertThat(restored.bounds.y).isEqualTo(10f)
    }

    @Test fun `locked element cannot be moved`() {
        val el = DesignElement.Shape(
            id = "e1", bounds = Bounds(10f, 10f, 100f, 100f), locked = true,
            kind = ShapeKind.RECTANGLE
        )
        val h = DesignHistory(sampleDoc(elements = listOf(el)))
        val r = h.push(DesignCommand.MoveElement("c1", "e1", 5f, 0f))
        assertThat(r.isErr).isTrue()
        // State is unchanged.
        assertThat(h.state.value.elements.first().bounds.x).isEqualTo(10f)
    }

    @Test fun `coalesce merges continuous drag`() {
        val el = DesignElement.Shape(
            id = "e1", bounds = Bounds(0f, 0f, 50f, 50f), kind = ShapeKind.RECTANGLE
        )
        val h = DesignHistory(sampleDoc(elements = listOf(el)))
        h.beginCoalesce("move:e1")
        repeat(50) { i ->
            h.push(DesignCommand.MoveElement("c$i", "e1", 1f, 1f))
        }
        h.endCoalesce()
        assertThat(h.state.value.elements.first().bounds.x).isEqualTo(50f)
        h.undo()
        // One undo restores the entire drag — coalescing works.
        assertThat(h.state.value.elements.first().bounds.x).isEqualTo(0f)
    }

    @Test fun `redo re-applies after undo`() {
        val el = DesignElement.Shape(
            id = "e1", bounds = Bounds(0f, 0f, 50f, 50f), kind = ShapeKind.RECTANGLE
        )
        val h = DesignHistory(sampleDoc(elements = listOf(el)))
        h.push(DesignCommand.MoveElement("c1", "e1", 100f, 0f))
        h.undo()
        h.redo()
        assertThat(h.state.value.elements.first().bounds.x).isEqualTo(100f)
    }
}

internal fun sampleDoc(
    elements: List<DesignElement> = emptyList(),
): DesignDocument = DesignDocument(
    id = "test-doc",
    name = "Test",
    canvas = CanvasSpec(1080f, 1620f),
    background = BackgroundSpec.Solid("#FFFFFF"),
    palette = PaletteSpec(
        primary = "#000000", secondary = "#FFFFFF", accent = "#3F51B5",
        onPrimary = "#FFFFFF", onSecondary = "#000000",
    ),
    typography = TypographySpec.Default,
    elements = elements,
    metadata = DocumentMetadata(createdAt = 1L, updatedAt = 2L),
    seed = 1234L,
)
