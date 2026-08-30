package com.adnanfoisal.infinitydesign.export.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.core.util.SafeMath
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.export.png.AndroidCanvasSurface
import com.adnanfoisal.infinitydesign.export.png.SkiaRendererAndroid
import com.adnanfoisal.infinitydesign.graphics.procedural.ProceduralRegistry
import com.adnanfoisal.infinitydesign.graphics.renderer.RenderQuality
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * PDF export — uses Android's framework PdfDocument which is reliable and
 * vector-aware for shapes (text is rendered via Skia inside the document canvas).
 *
 * Section 52: do not implement a fragile handwritten PDF generator.
 */
class PdfExporter(private val registry: ProceduralRegistry) {

    fun exportBytes(doc: DesignDocument, pageWidth: Int = 1080, pageHeight: Int = 1620): AppResult<ByteArray> {
        if (!SafeMath.allFinite(doc.canvas.width, doc.canvas.height)) {
            return errResult(AppError.Kind.InvalidDimension, "canvas non-finite")
        }
        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val scale = pageWidth.toFloat() / doc.canvas.width
            canvas.scale(scale, scale)
            val surface = AndroidCanvasSurface(canvas, registry)
            SkiaRendererAndroid(registry).render(doc, surface, RenderQuality.EXPORT_HIGH)
            document.finishPage(page)
            val baos = ByteArrayOutputStream()
            document.writeTo(baos)
            return okResult(baos.toByteArray())
        } catch (e: Throwable) {
            return errResult(AppError.Kind.RendererFailure, "PDF export failed: ${e.message}", e)
        } finally {
            document.close()
        }
    }

    fun exportToStream(doc: DesignDocument, out: OutputStream, pageWidth: Int = 1080, pageHeight: Int = 1620): AppResult<Unit> =
        when (val r = exportBytes(doc, pageWidth, pageHeight)) {
            is AppResult.Ok -> { out.write(r.value); okResult(Unit) }
            is AppResult.Err -> r
        }
}
