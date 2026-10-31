package com.storeinvoice.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PType(var name: String, var hsn: String, var cgst: String, var sgst: String)
class Product(var id: String, var type: String, var name: String, var code: String, var price: String, var cgst: String, var sgst: String, var image: String)
class Customer(var id: String, var p: Party)
class StoreInfo(var name: String, var a1: String, var a2: String, var a3: String, var phone: String, var gst: String)
class Settings(var till: String, var createdBy: String, var payment: String, var taxMode: String)
class InvRec(val orderNo: String, val createdAt: Long, val dateTime: String, val customer: String, val total: String, val data: InvoiceData)

class Repo(private val ctx: Context) {
    companion object { const val SCHEMA = 1 }

    val types = ArrayList<PType>()
    val products = ArrayList<Product>()
    val customers = ArrayList<Customer>()
    val invoices = ArrayList<InvRec>()
    var store = StoreInfo("Parkar", "Nmum-Kharghar-A116", "Navi Mumbai-410210", "", "18099985662/040-49462310", "27ADFFGHHH6H1ZE")
    var settings = Settings("1100", "Vignesh Patil", "UPI", "INCLUSIVE")
    private val rnd = SecureRandom()
    private val file get() = File(ctx.filesDir, "data.json")
    val backupDir get() = File(ctx.filesDir, "backups").also { it.mkdirs() }
    val pdfDir get() = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "invoices").also { it.mkdirs() }

    init {
        if (file.exists()) {
            try { apply(JSONObject(file.readText()), false) } catch (e: Exception) { file.copyTo(File(backupDir, "corrupt_${System.currentTimeMillis()}.json"), true); defaults() }
        } else defaults()
        if (types.isEmpty()) defaults()
    }

    private fun defaults() {
        types.clear()
        listOf("Trimmer" to "85102000", "Wired Earphone" to "85183020", "Neckband" to "85183020", "Headset" to "85183010", "Wireless Earphone" to "85183020")
            .forEach { types.add(PType(it.first, it.second, "9", "9")) }
        save()
    }

    fun newId() = java.lang.Long.toString(System.currentTimeMillis(), 36) + rnd.nextInt(1296).toString(36)
    fun digits(n: Int) = (1..n).map { rnd.nextInt(10) }.joinToString("")
    fun newProductCode(): String { var c: String; do { c = (100000 + rnd.nextInt(900000)).toString() } while (products.any { it.code == c }); return c }
    fun newOrderNo(): String { var o: String; do { o = "SOA" + digits(12) } while (invoices.any { it.orderNo == o }); return o }
    fun newTxnId() = "RT" + digits(15)
    fun now(): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())

    // ---------- JSON ----------
    private fun JSONObject.s(k: String) = optString(k, "")
    private fun partyJ(p: Party) = JSONObject().put("name", p.name).put("addr1", p.addr1).put("addr2", p.addr2).put("city", p.city).put("state", p.state).put("pin", p.pin).put("phone", p.phone).put("email", p.email)
    private fun partyF(o: JSONObject) = Party(o.s("name"), o.s("addr1"), o.s("addr2"), o.s("city"), o.s("state"), o.s("pin"), o.s("phone"), o.s("email"))

    fun invToJson(d: InvoiceData) = JSONObject().put("storeName", d.storeName).put("sAddr1", d.sAddr1).put("sAddr2", d.sAddr2).put("sAddr3", d.sAddr3)
        .put("sPhone", d.sPhone).put("gst", d.gst).put("till", d.till).put("orderNo", d.orderNo).put("createdBy", d.createdBy).put("dateTime", d.dateTime)
        .put("customer", partyJ(d.customer)).put("payMethod", d.payMethod).put("last4", d.last4).put("txnRef", d.txnRef).put("transactionId", d.transactionId)
        .put("taxMode", d.taxMode).put("items", JSONArray().also { a -> d.items.forEach { i ->
            a.put(JSONObject().put("name", i.name).put("type", i.type).put("code", i.code).put("hsn", i.hsn).put("qty", i.qty)
                .put("unit", i.unit.toPlainString()).put("cgst", i.cgst.toPlainString()).put("sgst", i.sgst.toPlainString())) } })

    fun invFromJson(o: JSONObject): InvoiceData {
        val items = ArrayList<Item>(); val a = o.getJSONArray("items")
        for (k in 0 until a.length()) { val i = a.getJSONObject(k)
            items.add(Item(i.s("name"), i.s("type"), i.s("code"), i.s("hsn"), i.getInt("qty"), BigDecimal(i.getString("unit")), BigDecimal(i.getString("cgst")), BigDecimal(i.getString("sgst")))) }
        return InvoiceData(o.s("storeName"), o.s("sAddr1"), o.s("sAddr2"), o.s("sAddr3"), o.s("sPhone"), o.s("gst"), o.s("till"), o.getString("orderNo"), o.s("createdBy"), o.s("dateTime"),
            partyF(o.getJSONObject("customer")), o.s("payMethod"), o.s("last4"), o.s("txnRef"), o.s("transactionId"), o.optString("taxMode", "INCLUSIVE"), items)
    }

    fun toJson(): JSONObject {
        val j = JSONObject().put("schemaVersion", SCHEMA).put("app", "StoreInvoiceGenerator").put("exportedAt", System.currentTimeMillis())
        j.put("productTypes", JSONArray().also { a -> types.forEach { a.put(JSONObject().put("name", it.name).put("hsn", it.hsn).put("cgst", it.cgst).put("sgst", it.sgst)) } })
        j.put("products", JSONArray().also { a -> products.forEach { a.put(JSONObject().put("id", it.id).put("type", it.type).put("name", it.name).put("code", it.code).put("unitPrice", it.price).put("cgst", it.cgst).put("sgst", it.sgst).put("imageUrl", it.image)) } })
        j.put("customers", JSONArray().also { a -> customers.forEach { a.put(partyJ(it.p).put("id", it.id)) } })
        j.put("store", JSONObject().put("name", store.name).put("addr1", store.a1).put("addr2", store.a2).put("addr3", store.a3).put("phone", store.phone).put("gst", store.gst))
        j.put("settings", JSONObject().put("till", settings.till).put("createdBy", settings.createdBy).put("payment", settings.payment).put("taxMode", settings.taxMode))
        j.put("invoices", JSONArray().also { a -> invoices.forEach { a.put(JSONObject().put("orderNo", it.orderNo).put("createdAt", it.createdAt).put("dateTime", it.dateTime).put("customer", it.customer).put("total", it.total).put("data", invToJson(it.data))) } })
        return j
    }

    /** Returns null when valid, otherwise a human-readable problem. */
    fun validate(j: JSONObject): String? {
        try {
            if (j.optString("app") != "StoreInvoiceGenerator") return "Not a Store Invoice Generator backup."
            if (j.getInt("schemaVersion") > SCHEMA) return "Backup was made by a newer app version."
            for (k in listOf("productTypes", "products", "customers", "invoices")) j.getJSONArray(k)
            j.getJSONObject("store"); j.getJSONObject("settings")
            val pt = j.getJSONArray("productTypes"); if (pt.length() == 0) return "No product types in file."
            for (i in 0 until pt.length()) { val o = pt.getJSONObject(i); o.getString("name"); BigDecimal(o.getString("cgst")); BigDecimal(o.getString("sgst")) }
            val p = j.getJSONArray("products")
            for (i in 0 until p.length()) { val o = p.getJSONObject(i); o.getString("id"); o.getString("name"); BigDecimal(o.getString("unitPrice")); BigDecimal(o.getString("cgst")); BigDecimal(o.getString("sgst")) }
            val c = j.getJSONArray("customers"); for (i in 0 until c.length()) { c.getJSONObject(i).getString("id") }
            val v = j.getJSONArray("invoices"); for (i in 0 until v.length()) { val o = v.getJSONObject(i); o.getString("orderNo"); invFromJson(o.getJSONObject("data")) }
        } catch (e: Exception) { return "Invalid backup file: ${e.message}" }
        return null
    }

    private fun apply(j: JSONObject, merge: Boolean) {
        if (!merge) { types.clear(); products.clear(); customers.clear(); invoices.clear() }
        val pt = j.getJSONArray("productTypes")
        for (i in 0 until pt.length()) { val o = pt.getJSONObject(i)
            val t = PType(o.s("name"), o.s("hsn"), o.s("cgst"), o.s("sgst")); if (types.none { it.name == t.name }) types.add(t) }
        val p = j.getJSONArray("products")
        for (i in 0 until p.length()) { val o = p.getJSONObject(i)
            if (products.none { it.id == o.s("id") }) products.add(Product(o.s("id"), o.s("type"), o.s("name"), o.s("code"), o.s("unitPrice"), o.s("cgst"), o.s("sgst"), o.s("imageUrl"))) }
        val c = j.getJSONArray("customers")
        for (i in 0 until c.length()) { val o = c.getJSONObject(i); if (customers.none { it.id == o.s("id") }) customers.add(Customer(o.s("id"), partyF(o))) }
        val v = j.getJSONArray("invoices")
        for (i in 0 until v.length()) { val o = v.getJSONObject(i)
            if (invoices.none { it.orderNo == o.s("orderNo") }) invoices.add(InvRec(o.s("orderNo"), o.optLong("createdAt"), o.s("dateTime"), o.s("customer"), o.s("total"), invFromJson(o.getJSONObject("data")))) }
        if (!merge) {
            val s = j.getJSONObject("store"); store = StoreInfo(s.s("name"), s.s("addr1"), s.s("addr2"), s.s("addr3"), s.s("phone"), s.s("gst"))
            val t = j.getJSONObject("settings"); settings = Settings(t.s("till"), t.s("createdBy"), t.s("payment"), t.optString("taxMode", "INCLUSIVE"))
        }
    }

    fun save() { // atomic write
        val tmp = File(ctx.filesDir, "data.json.tmp")
        tmp.writeText(toJson().toString(2))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    fun backup(label: String = "backup"): File {
        val f = File(backupDir, "${label}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json")
        f.writeText(toJson().toString(2)); return f
    }
    fun backups(): List<File> = backupDir.listFiles { x -> x.name.endsWith(".json") && !x.name.startsWith("corrupt") }?.sortedByDescending { it.name } ?: emptyList()

    /** Validates, takes an automatic safety backup, then merges or replaces. Returns error text or null. */
    fun importJson(text: String, merge: Boolean): String? {
        val j = try { JSONObject(text) } catch (e: Exception) { return "File is not valid JSON." }
        validate(j)?.let { return it }
        backup("before-import")
        apply(j, merge); save(); return null
    }

    fun typeOf(name: String) = types.firstOrNull { it.name == name }
}
