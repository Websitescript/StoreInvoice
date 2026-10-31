package com.storeinvoice.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File

object PdfExport {
    /** Courier New is not redistributable; if the owner drops CourierNew.ttf into assets it is used, otherwise
     *  Liberation Mono (metric-compatible, identical 0.6 em advance) is used so every glyph lands in the same place. */
    private fun mono(ctx: Context): Typeface {
        for (n in listOf("CourierNew.ttf", "cour.ttf", "LiberationMono-Regular.ttf")) {
            try { return Typeface.createFromAsset(ctx.assets, n) } catch (e: Exception) { }
        }
        return Typeface.MONOSPACE
    }

    fun write(ctx: Context, d: InvoiceData, out: File) {
        val pages = InvoiceLayout.build(d)
        val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono(ctx); textSize = InvoiceLayout.FONT; color = Color.BLACK }
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = try { Typeface.createFromAsset(ctx.assets, "DejaVuSans.ttf") } catch (e: Exception) { Typeface.SANS_SERIF }
            textSize = InvoiceLayout.FONT; color = Color.BLACK; textAlign = Paint.Align.CENTER
        }
        val bp = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val doc = PdfDocument()
        pages.forEachIndexed { i, cmds ->
            // Android page sizes are whole points: 842.5 pt is rounded up to 843 and the MediaBox is patched back below.
            val page = doc.startPage(PdfDocument.PageInfo.Builder(216, 843, i + 1).create())
            val c: Canvas = page.canvas
            for (cmd in cmds) when (cmd) {
                is Cmd.T -> c.drawText(cmd.text, cmd.x, cmd.y + InvoiceLayout.ASCENT, mp)
                is Cmd.Cap -> c.drawText(cmd.text, cmd.cx, cmd.baseline, sp)
                is Cmd.Bars -> {
                    val x0 = cmd.cx - cmd.bits.size * cmd.unit / 2
                    var k = 0
                    while (k < cmd.bits.size) {
                        if (cmd.bits[k]) {
                            var j = k; while (j < cmd.bits.size && cmd.bits[j]) j++
                            c.drawRect(x0 + k * cmd.unit, cmd.y, x0 + j * cmd.unit, cmd.y + cmd.h, bp); k = j
                        } else k++
                    }
                }
            }
            doc.finishPage(page)
        }
        val bos = ByteArrayOutputStream(); doc.writeTo(bos); doc.close()
        out.parentFile?.mkdirs()
        out.writeBytes(patchMediaBox(bos.toByteArray()))
    }

    /** Same-length replacement of "/MediaBox [0 0 216 843]" by "[0 0 216 842.5]" so xref offsets stay valid. */
    private fun patchMediaBox(b: ByteArray): ByteArray {
        val s = String(b, Charsets.ISO_8859_1)
        val re = Regex("/MediaBox\\s*\\[([^\\]]*)\\]")
        val sb = StringBuilder(); var last = 0
        for (m in re.findAll(s)) {
            val old = m.value
            val neu = "/MediaBox [0 0 216 842.5]"
            val rep = if (old.length >= neu.length && Regex("^\\s*0\\s+0\\s+216\\s+843\\s*$").matches(m.groupValues[1])) neu.padEnd(old.length, ' ') else old
            sb.append(s, last, m.range.first).append(rep); last = m.range.last + 1
        }
        sb.append(s, last, s.length)
        return sb.toString().toByteArray(Charsets.ISO_8859_1)
    }

    /** Renders each PDF page to a bitmap for the in-app preview. */
    fun render(file: File, width: Int = 900): List<Bitmap> {
        val out = ArrayList<Bitmap>()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(pfd)
        for (i in 0 until r.pageCount) {
            val p = r.openPage(i)
            val h = (width * p.height.toFloat() / p.width).toInt()
            val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888); bmp.eraseColor(Color.WHITE)
            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); p.close(); out.add(bmp)
        }
        r.close(); pfd.close(); return out
    }
}
