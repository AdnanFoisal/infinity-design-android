package com.adnanfoisal.infinitydesign.export.png

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.adnanfoisal.infinitydesign.core.result.AppError
import com.adnanfoisal.infinitydesign.core.result.AppResult
import com.adnanfoisal.infinitydesign.core.result.errResult
import com.adnanfoisal.infinitydesign.core.result.okResult
import com.adnanfoisal.infinitydesign.core.util.SafeMath
import com.adnanfoisal.infinitydesign.design.dsl.DesignDocument
import com.adnanfoisal.infinitydesign.graphics.procedural.ProceduralRegistry
import com.adnanfoisal.infinitydesign.graphics.renderer.RenderQuality
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * PNG export. Section 50/51: configurable resolution, no OOM on huge exports.
 *
 * Memory budget is computed before allocation. If it exceeds a configurable
 * limit (default 256MB), the caller must use a lower scale or tile the export.
 */
class PngExporter(private val registry: ProceduralRegistry) {

    fun exportBytes(doc: DesignDocument, scale: Float = 1f, format: Format = Format.PNG_100): AppResult<ByteArray> {
        val w = (doc.canvas.width * scale).toInt().coerceIn(1, 8000)
        val h = (doc.canvas.height * scale).toInt().coerceIn(1, 8000)
        // Memory check — 4 bytes per pixel.
        val memBytes = w.toLong() * h.toLong() * 4L
        if (memBytes > MAX_BITMAP_BYTES) {
            return errResult(AppError.Kind.OutOfMemory, "PNG too large: ${memBytes / 1024 / 1024}MB")
        }
        if (!SafeMath.allFinite(doc.canvas.width, doc.canvas.height)) {
            return errResult(AppError.Kind.InvalidDimension, "canvas has non-finite dimensions")
        }
        val bitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            return errResult(AppError.Kind.OutOfMemory, "Bitmap allocation failed", oom)
        } catch (e: Throwable) {
            return errResult(AppError.Kind.RendererFailure, "Bitmap creation failed: ${e.message}", e)
        }
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val surface = AndroidCanvasSurface(canvas, registry)
        SkiaRendererAndroid(registry).render(doc, surface, RenderQuality.EXPORT_HIGH)
        val baos = ByteArrayOutputStream()
        val compressed = try {
            bitmap.compress(format.androidFormat, format.quality, baos)
        } catch (e: Throwable) {
            return errResult(AppError.Kind.RendererFailure, "PNG compress failed: ${e.message}", e)
        }
        bitmap.recycle()
        if (!compressed) return errResult(AppError.Kind.RendererFailure, "PNG compress returned false")
        return okResult(baos.toByteArray())
    }

    fun exportToStream(doc: DesignDocument, scale: Float, format: Format, out: OutputStream): AppResult<Unit> {
        return when (val r = exportBytes(doc, scale, format)) {
            is AppResult.Ok -> { out.write(r.value); okResult(Unit) }
            is AppResult.Err -> r
        }
    }

    enum class Format(val androidFormat: Bitmap.CompressFormat, val quality: Int) {
        PNG_100(Bitmap.CompressFormat.PNG, 100),
        PNG_80(Bitmap.CompressFormat.PNG, 80),
        WEBP_90(Bitmap.CompressFormat.WEBP, 90),
    }

    companion object {
        const val MAX_BITMAP_BYTES: Long = 256L * 1024L * 1024L
    }
}
