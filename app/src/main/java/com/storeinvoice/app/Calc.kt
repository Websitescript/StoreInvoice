package com.storeinvoice.app

import java.math.BigDecimal
import java.math.RoundingMode

data class Item(
    val name: String, val type: String, val code: String, val hsn: String,
    val qty: Int, val unit: BigDecimal, val cgst: BigDecimal, val sgst: BigDecimal
)

data class Party(
    val name: String = "", val addr1: String = "", val addr2: String = "", val city: String = "",
    val state: String = "", val pin: String = "", val phone: String = "", val email: String = ""
)

data class InvoiceData(
    val storeName: String, val sAddr1: String, val sAddr2: String, val sAddr3: String,
    val sPhone: String, val gst: String,
    val till: String, val orderNo: String, val createdBy: String, val dateTime: String,
    val customer: Party, val payMethod: String, val last4: String, val txnRef: String,
    val transactionId: String, val taxMode: String, val items: List<Item>,
    val legal: String = Defaults.LEGAL, val cin: String = Defaults.CIN,
    val regd1: String = Defaults.REGD1, val regd2: String = Defaults.REGD2, val regd3: String = Defaults.REGD3,
    val site: String = Defaults.SITE, val email: String = Defaults.EMAIL
)

object Defaults {
    const val LEGAL = "Falah Retail Limited Trading as"
    const val CIN = "U31988MH2020PLC998115"
    const val REGD1 = "Regd. Office - Unit No. 701 & 702,"
    const val REGD2 = "7th Floor, Khed,"
    const val REGD3 = "Ratnagiri,  415710,India"
    const val SITE = "falah.com"
    const val EMAIL = "customersupport@falah.com"
}

data class Line(val item: Item, val total: BigDecimal, val taxable: BigDecimal, val cgstAmt: BigDecimal, val sgstAmt: BigDecimal)
data class Group(val cgst: BigDecimal, val sgst: BigDecimal, val total: BigDecimal, val cgstAmt: BigDecimal, val sgstAmt: BigDecimal)
data class Totals(val lines: List<Line>, val subtotal: BigDecimal, val cgst: BigDecimal, val sgst: BigDecimal, val grand: BigDecimal, val groups: List<Group>)

object Calc {
    private val H = BigDecimal(100)
    fun r2(v: BigDecimal): BigDecimal = v.setScale(2, RoundingMode.HALF_UP)

    /** taxMode INCLUSIVE: prices include tax (tax extracted). ORIGINAL: tax shown as rate x gross, like the store's legacy system. */
    fun compute(items: List<Item>, taxMode: String): Totals {
        val lines = items.map { it ->
            val total = r2(it.unit.multiply(BigDecimal(it.qty)))
            if (taxMode == "ORIGINAL") {
                Line(it, total, total, r2(total.multiply(it.cgst).divide(H)), r2(total.multiply(it.sgst).divide(H)))
            } else {
                val taxable0 = total.multiply(H).divide(H.add(it.cgst).add(it.sgst), 2, RoundingMode.HALF_UP)
                val c = r2(taxable0.multiply(it.cgst).divide(H))
                val s = r2(taxable0.multiply(it.sgst).divide(H))
                Line(it, total, total.subtract(c).subtract(s), c, s)
            }
        }
        val groups = lines.groupBy { it.item.cgst.stripTrailingZeros() to it.item.sgst.stripTrailingZeros() }.map { (k, v) ->
            Group(k.first, k.second, v.fold(BigDecimal.ZERO) { a, l -> a + l.total },
                v.fold(BigDecimal.ZERO) { a, l -> a + l.cgstAmt }, v.fold(BigDecimal.ZERO) { a, l -> a + l.sgstAmt })
        }
        val grand = lines.fold(BigDecimal.ZERO) { a, l -> a + l.total }
        return Totals(lines, lines.fold(BigDecimal.ZERO) { a, l -> a + l.taxable },
            lines.fold(BigDecimal.ZERO) { a, l -> a + l.cgstAmt }, lines.fold(BigDecimal.ZERO) { a, l -> a + l.sgstAmt }, grand, groups)
    }
}

object States {
    val codes = mapOf(
        "jammu and kashmir" to 1, "himachal pradesh" to 2, "punjab" to 3, "chandigarh" to 4, "uttarakhand" to 5,
        "haryana" to 6, "delhi" to 7, "rajasthan" to 8, "uttar pradesh" to 9, "bihar" to 10, "sikkim" to 11,
        "arunachal pradesh" to 12, "nagaland" to 13, "manipur" to 14, "mizoram" to 15, "tripura" to 16,
        "meghalaya" to 17, "assam" to 18, "west bengal" to 19, "jharkhand" to 20, "odisha" to 21,
        "chhattisgarh" to 22, "madhya pradesh" to 23, "gujarat" to 24, "dadra and nagar haveli and daman and diu" to 26,
        "maharashtra" to 27, "karnataka" to 29, "goa" to 30, "lakshadweep" to 31, "kerala" to 32, "tamil nadu" to 33,
        "puducherry" to 34, "andaman and nicobar islands" to 35, "telangana" to 36, "andhra pradesh" to 37, "ladakh" to 38
    )
    val names = codes.keys.map { k -> k.split(" ").joinToString(" ") { w -> if (w == "and") w else w.replaceFirstChar { it.uppercase() } } }.sorted()
    /** "Maharashtra" -> "Maharashtra-27"; unknown or already suffixed names pass through unchanged. */
    fun label(state: String): String {
        val s = state.trim()
        if (s.isEmpty() || s.contains('-')) return s
        val c = codes[s.lowercase()] ?: return s
        return "$s-$c"
    }
}
