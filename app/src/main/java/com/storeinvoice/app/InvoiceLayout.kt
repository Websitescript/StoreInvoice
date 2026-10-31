package com.storeinvoice.app

import java.math.BigDecimal

sealed class Cmd {
    /** y = top of the text line (PDF "top" coordinate, same convention as the original); mono = Courier-style font. */
    data class T(val x: Float, val y: Float, val text: String, val mono: Boolean = true) : Cmd()
    /** Centered-baseline text in the sans font (barcode captions). */
    data class Cap(val cx: Float, val baseline: Float, val text: String) : Cmd()
    data class Bars(val cx: Float, val y: Float, val unit: Float, val h: Float, val bits: BooleanArray) : Cmd()
}

/** Reproduces the store's thermal-receipt PDF: 216 x 842.5 pt, Courier New 8.5 pt, 40 columns, 10.52 pt line pitch. */
object InvoiceLayout {
    const val PAGE_W = 216f
    const val PAGE_H = 842.5f
    const val FONT = 8.5f
    const val ASCENT = 7.08f
    const val PITCH = 10.52f
    const val Y0 = 2.8f
    const val SLOTS = 80
    const val GROUP_H = 114f
    private const val X1 = -0.75f
    private const val X2 = -0.25f

    private val D1 = " " + "-".repeat(40)
    private val D2 = " " + "-".repeat(39) + " "

    private fun center(s: String, w: Int = 41): String = " ".repeat(maxOf(0, (w - s.length + 1) / 2)) + s
    private fun right(left: String, r: String, w: Int = 40): String = left + " ".repeat(maxOf(1, w - left.length - r.length)) + r

    private fun cols(vararg p: Pair<Int, String>): String {
        val c = CharArray(40) { ' ' }
        for ((at, s) in p) for (k in s.indices) if (at + k in 0..39) c[at + k] = s[k]
        return String(c)
    }
    private fun rcols(at: Int, s: String) = at - s.length + 1 to s

    fun wrap(text: String, w: Int): List<String> {
        val out = ArrayList<String>()
        for (para in text.split("\n")) {
            var line = StringBuilder()
            for (word0 in para.trim().split(Regex("\\s+"))) {
                var word = word0
                if (word.isEmpty()) continue
                while (word.length > w) {
                    if (line.isNotEmpty()) { out.add(line.toString()); line = StringBuilder() }
                    out.add(word.substring(0, w)); word = word.substring(w)
                }
                if (line.isEmpty()) line.append(word)
                else if (line.length + 1 + word.length <= w) line.append(' ').append(word)
                else { out.add(line.toString()); line = StringBuilder(word) }
            }
            if (line.isNotEmpty()) out.add(line.toString())
        }
        return out
    }

    private fun money(v: BigDecimal) = v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()

    private class Row(val text: String, val x: Float = X1, val keep: Int = 0)

    private fun flowRows(d: InvoiceData, t: Totals): List<Row> {
        val r = ArrayList<Row>()
        fun add(s: String, x: Float = X1) { r.add(Row(s, x)) }
        if (d.legal.isNotBlank()) add(center(d.legal, 39))
        if (d.storeName.isNotBlank()) add(center(d.storeName))
        add(center("Tax Invoice"))
        for (a in listOf(d.sAddr1, d.sAddr2, d.sAddr3)) if (a.isNotBlank()) wrap(a, 38).forEach { add(center(it)) }
        val ph = d.sPhone.trim()
        val slash = ph.indexOf('/')
        if (slash in 0 until ph.length - 1) {
            val first = "Phone Number : " + ph.substring(0, slash + 1).trim()
            add(center(first))
            add(" ".repeat(maxOf(0, (41 - first.length + 1) / 2 + 14)) + ph.substring(slash + 1).trim())
        } else add(center("Phone Number : $ph"))
        add(center("GST No: ${d.gst}"))
        add(D1)
        add(" Till No:${d.till}")
        add(" Order Number: ${d.orderNo}")
        add(" Created By: ${d.createdBy}")
        add(" Date & Time:${d.dateTime}")
        add(D2)
        val c = d.customer
        add("Bill to address:", X2)
        fun ind(s: String) { wrap(s, 34).forEach { add("      $it") } }
        if (c.name.isNotBlank()) ind(c.name)
        if (c.addr1.isNotBlank()) ind(c.addr1)
        if (c.addr2.isNotBlank()) ind(c.addr2)
        if (c.city.isNotBlank()) ind(c.city)
        val st = States.label(c.state)
        if (st.isNotBlank()) ind(st)
        if (c.pin.isNotBlank()) ind(c.pin)
        if (c.phone.isNotBlank()) add(c.phone, X2)
        if (c.email.isNotBlank()) add(c.email, X2)
        add("Place of Supply: $st", X2)
        add(D1)
        add(" Item Description ")
        add(cols(1 to "Item Code", 19 to "Tax", rcols(39, "Qty")))
        add(cols(1 to "Price", 16 to "Total Amt", rcols(39, "Del_Type")))
        add(cols(1 to "DemoY/N", 17 to "InstY/N", 27 to "Delivery Date"))
        add(" HSN CODE ")
        add(" SN/IMEI ")
        add(D1)
        add(" ------------- SELF-PICKUP --------------")
        add("       Invoice No: ${d.orderNo}")
        for (l in t.lines) {
            val it = l.item
            val start = r.size
            wrap("ZPLU - ${it.name}", 39).forEach { s -> add(" $s") }
            add(" ${it.type}")
            add(cols(1 to it.code, 18 to "62/52", rcols(39, "${it.qty}/EA")))
            add(cols(1 to money(it.unit), 18 to money(l.total), 36 to "STOR"))
            add(cols(1 to "NA", 19 to "NA", 38 to "NA"))
            add(" HSN CODE: ${it.hsn}")
            add("")
            r[start] = Row(r[start].text, r[start].x, r.size - start - 1)
        }
        add(D1)
        add(right(" TOTAL:", "INR ${money(t.grand)}"))
        add(D1)
        add(center("Payment Details"))
        add(right(" ${d.payMethod}", "INR ${money(t.grand)}"))
        add(" LastFourDigits: ${d.last4}")
        add(" Txn RefId: ${d.txnRef}")
        add("")
        add(D1)
        add(" Tax Summary ")
        add(cols(1 to "Type", 7 to "Code", 13 to "Rate", 21 to "Total Amt", 33 to "Tax Amt"))
        for (g in t.groups) {
            add(cols(1 to "SGST", 7 to "62", 13 to g.sgst.setScale(4).toPlainString(), rcols(29, money(g.total)), rcols(39, money(g.sgstAmt))))
            add(cols(1 to "CGST", 7 to "52", 13 to g.cgst.setScale(4).toPlainString(), rcols(29, money(g.total)), rcols(39, money(g.cgstAmt))))
        }
        add(" PRICES INCLUSIVE OF ALL TAXES ")
        add(D1)
        add(D1)
        if (d.cin.isNotBlank()) add("       CIN : ${d.cin}")
        add("")
        if (d.regd1.isNotBlank()) add("    ${d.regd1} ")
        if (d.regd2.isNotBlank()) add("     ${d.regd2}")
        if (d.regd3.isNotBlank()) add("    ${d.regd3}")
        add("")
        add("")
        add("          Thank you for shopping with us.", X2)
        add("        Buy yourself an electronics life,", X2)
        if (d.site.isNotBlank()) add(" ".repeat(maxOf(0, 37 - "shop at ${d.site}".length)) + "shop at ${d.site}", X2)
        if (d.email.isNotBlank()) add(" ".repeat(maxOf(0, 36 - "Email:${d.email}".length)) + "Email:${d.email}")
        return r
    }

    private fun terms(site: String): List<String> = listOf(
        " " + "-".repeat(39) + "  ", "Exchange Policy / Terms and Conditions ", "",
        "1. Products that require Home Delivery ", "like LED TVs, Home Theatres, Air ", "Conditioners, Washing Machines, ",
        "Refrigerators etc will only be exchanged ", "by $site in case of inherent",
        "manufacturing or transit defects which ", "are found before, during or at the time ", "of demo/installation. Warranty period ",
        "for the product will start from the ", "date mentioned on the Invoice. ",
        "2. The product to be exchanged must be ", "returned without any scratches, dents, ", "tears or holes and with all the ",
        "accessories and manuals in the original ", "manufacturer's undamaged & saleable ", "box/packaging. ",
        "3. Any defects arising in the product ", "post-delivery and installation, will be ", "serviced by the manufacturer/brand ",
        "service center as per the ", "manufacturer's brand warranty policy. ", "${site.replaceFirstChar { it.uppercase() }} will facilitate the service from",
        "the respective manufacturer as per the ", "manufacturer's/brand's warranty items. ",
        "4. Personal Care products will not be ", "exchanged for hygiene reasons. ",
        "5. Free Gifts will not be ", "exchanged/replaced or covered under ", "any kind of warranty. ",
        "6. Wherever applicable, GST is levied at ", "applicable rate on the value determined ", "as per Rule 32(5) of the CGST Rules. ",
        "7. Whether tax payable on reverse ", "charge : No. ",
        "8. Please note that there would be ", "facilitation charges applicable for all ", "carry in products accepted at stores ",
        "for repairs or replacement. ", "-".repeat(39) + " "
    )

    fun build(d: InvoiceData): List<List<Cmd>> {
        val t = Calc.compute(d.items, d.taxMode)
        val rows = flowRows(d, t)
        val pages = ArrayList<MutableList<Cmd>>()
        var cur = ArrayList<Cmd>(); pages.add(cur)
        var slot = 0
        for (row in rows) {
            if (slot >= SLOTS || (row.keep > 0 && slot + row.keep + 1 > SLOTS)) { cur = ArrayList(); pages.add(cur); slot = 0 }
            if (row.text.isNotEmpty()) cur.add(Cmd.T(row.x, Y0 + slot * PITCH, row.text))
            slot++
        }
        // barcode block follows the content directly (for a one-item invoice this is exactly the original position)
        var base = Y0 + slot * PITCH
        if (base + GROUP_H > PAGE_H) { cur = ArrayList(); pages.add(cur); base = Y0 }
        fun at(orig: Float) = base + (orig - 717.57f)
        cur.add(Cmd.T(X1, at(717.57f), D1))
        cur.add(Cmd.T(X1, at(728.32f), center("Transaction ID", 41)))
        cur.add(Cmd.Bars(108f, at(737.84f), 0.595f, 23.6f, Code128.modules(d.transactionId)))
        cur.add(Cmd.Cap(108f, at(768.9f), d.transactionId))
        cur.add(Cmd.T(X1, at(778.07f), " ---------")); cur.add(Cmd.T(163.7f, at(778.07f), "--------"))
        cur.add(Cmd.T(X2, at(788.6f), "----------")); cur.add(Cmd.T(163.7f, at(788.6f), "--------"))
        cur.add(Cmd.T(X2, at(799.1f), "-----"))
        cur.add(Cmd.Bars(108f, at(797.95f), 0.595f, 23.6f, Code128.modules(d.orderNo)))
        cur.add(Cmd.Cap(108f, at(829.0f), d.orderNo))
        cur.add(Cmd.T(163.7f, at(810.1f), "Order ID"))
        // terms: straight after the barcodes when they fit on the same page, otherwise on a fresh page (no blank gaps)
        val tl = terms(d.site)
        var ty = base + GROUP_H + 4f
        var tc = cur
        if (ty + tl.size * PITCH > PAGE_H) { tc = ArrayList(); pages.add(tc); ty = 1.85f }
        tl.forEachIndexed { i, s -> if (s.isNotEmpty()) tc.add(Cmd.T(X2, ty + i * PITCH, s)) }
        return pages
    }
}
