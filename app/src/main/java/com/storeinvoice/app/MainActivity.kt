package com.storeinvoice.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

class MainActivity : Activity() {
    lateinit var repo: Repo
    lateinit var ui: Ui
    private lateinit var content: FrameLayout
    private lateinit var titleTv: TextView
    private lateinit var inv: InvoiceScreen
    private var current = "home"
    private var pendingFile: File? = null
    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        repo = Repo(this); ui = Ui(this); inv = InvoiceScreen(this)
        titleTv = ui.text("Store Invoice Generator", 18f, true, Color.WHITE).apply { setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14)); setBackgroundColor(ui.accent) }
        content = FrameLayout(this)
        val nav = LinearLayout(this).apply { setBackgroundColor(Color.WHITE) }
        listOf("home" to "Home", "invoice" to "Invoice", "products" to "Products", "customers" to "Customers", "history" to "History").forEach { (k, l) ->
            nav.addView(TextView(this).apply { text = l; textSize = 12f; gravity = Gravity.CENTER; setTextColor(ui.accent); setTypeface(null, Typeface.BOLD); setPadding(0, ui.dp(12), 0, ui.dp(12)); setOnClickListener { go(k) } }, ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        setContentView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F3F6F8")); addView(titleTv); addView(content, ui.lp(h = 0, wt = 1f)); addView(nav) })
        go("home")
    }

    private fun show(v: View, title: String) { content.removeAllViews(); content.addView(v); titleTv.text = title }

    fun go(k: String) {
        current = k
        when (k) {
            "home" -> home(); "invoice" -> show(inv.view(), "Create Invoice"); "products" -> products(); "customers" -> customers(); "history" -> history()
            "store" -> storeSettings(); "types" -> types(); "backup" -> backup()
        }
    }
    override fun onBackPressed() { if (current != "home") go("home") else super.onBackPressed() }

    private fun stateField(v: String) = AutoCompleteTextView(this).apply { hint = "State"; setText(v); setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, States.names)); threshold = 1; setSingleLine() }

    // ---------------- Dashboard ----------------
    private fun stat(n: Int, l: String) = ui.card(ui.text(n.toString(), 24f, true, ui.accent), ui.text(l, 12f, false, ui.sub)).also { it.layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, 4) }
    private fun home(): Unit = show(ui.scroll(ui.col(
        ui.row(stat(repo.invoices.size, "Invoices"), stat(repo.products.size, "Products"), stat(repo.customers.size, "Customers")),
        ui.btn("＋ Create new invoice") { inv.reset(); go("invoice") }.also { it.layoutParams = ui.lp(m = 6) },
        ui.btn("Store settings", false) { go("store") }.also { it.layoutParams = ui.lp(m = 6) },
        ui.btn("Product types, HSN & tax", false) { go("types") }.also { it.layoutParams = ui.lp(m = 6) },
        ui.btn("Backup & restore", false) { go("backup") }.also { it.layoutParams = ui.lp(m = 6) })), "Dashboard")

    // ---------------- generic searchable list ----------------
    private fun searchList(title: String, add: String, onAdd: () -> Unit, rows: (String) -> List<View>) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun fill(q: String) { box.removeAllViews(); val r = rows(q); if (r.isEmpty()) box.addView(ui.text("Nothing here yet.", 14f, false, ui.sub)); r.forEach { box.addView(it) } }
        val s = ui.field("Search", "", ui.PLAIN) { fill(it) }; fill("")
        val head = if (add.isEmpty()) ui.col(s, pad = 0) else ui.col(ui.btn(add) { onAdd() }, s, pad = 0)
        show(ui.scroll(ui.col(head, box)), title)
    }

    // ---------------- Products ----------------
    private fun products(): Unit = searchList("Products", "＋ Add product", { productForm(null) }) { q ->
        repo.products.filter { q.isBlank() || "${it.name} ${it.type} ${it.code}".contains(q, true) }.map { p ->
            val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.parseColor("#E4E7EB")) }
            ui.loadImage(p.image, iv)
            ui.card(ui.row(iv.also { it.layoutParams = ui.lp(ui.dp(64), ui.dp(64), 0f, 4) },
                ui.col(ui.text(p.name, 15f, true), ui.text("${p.type} · code ${p.code} · HSN ${repo.typeOf(p.type)?.hsn ?: "-"}", 12f, false, ui.sub),
                    ui.text("${money.format(p.price.toBigDecimalOrNull() ?: BigDecimal.ZERO)} · CGST ${p.cgst}% · SGST ${p.sgst}%", 12f, false, ui.sub), pad = 4)),
                ui.row(ui.btn("Edit", false) { productForm(p) }, ui.btn("Delete", false) { ui.confirm("Delete ${p.name}? Saved invoices are not affected.") { repo.products.remove(p); repo.save(); products() } }))
        }
    }

    private fun productForm(p: Product?) {
        val names = repo.types.map { it.name }
        val hsnTv = ui.text("", 13f, false, ui.sub)
        val sel = maxOf(0, names.indexOf(p?.type))
        val name = ui.field("Item description / name", p?.name ?: "")
        val code = ui.field("Product code (6 digits)", p?.code ?: repo.newProductCode(), ui.NUM)
        val price = ui.field("Unit price (₹)", p?.price ?: "", ui.DEC)
        val cg = ui.field("CGST %", p?.cgst ?: repo.types[sel].cgst, ui.DEC); val sg = ui.field("SGST %", p?.sgst ?: repo.types[sel].sgst, ui.DEC)
        val img = ui.field("Image URL (https://…)", p?.image ?: "", ui.URI)
        var pickedType = names[sel]
        fun upd(i: Int, reset: Boolean) { pickedType = names[i]; hsnTv.text = "HSN code (from product type): ${repo.types[i].hsn}"; if (reset) { cg.setText(repo.types[i].cgst); sg.setText(repo.types[i].sgst) } }
        upd(sel, false)
        val sp = ui.spinner(names, sel) { upd(it, p == null) }
        ui.form(if (p == null) "Add product" else "Edit product", ui.text("Product type"), sp, hsnTv, name, code, price, cg, sg, img) {
            val c = code.text.toString().trim(); val pr = price.text.toString().trim().toBigDecimalOrNull()
            val c1 = cg.text.toString().trim().toBigDecimalOrNull(); val s1 = sg.text.toString().trim().toBigDecimalOrNull()
            val u = img.text.toString().trim()
            when {
                name.text.isBlank() -> "Enter the item name."
                c.length != 6 -> "Product code must be 6 digits."
                repo.products.any { it !== p && it.code == c } -> "That product code is already used."
                pr == null || pr.signum() < 0 -> "Enter a valid unit price."
                c1 == null || s1 == null || c1.signum() < 0 || s1.signum() < 0 || c1 > BigDecimal(50) || s1 > BigDecimal(50) -> "CGST/SGST must be between 0 and 50."
                u.isNotEmpty() && !(u.startsWith("http://") || u.startsWith("https://")) -> "Image link must start with http:// or https://"
                else -> { val n = name.text.toString().trim()
                    if (p == null) repo.products.add(Product(repo.newId(), pickedType, n, c, pr.toPlainString(), c1.toPlainString(), s1.toPlainString(), u))
                    else { p.type = pickedType; p.name = n; p.code = c; p.price = pr.toPlainString(); p.cgst = c1.toPlainString(); p.sgst = s1.toPlainString(); p.image = u }
                    repo.save(); products(); null }
            }
        }
    }

    // ---------------- Customers ----------------
    private fun customers(): Unit = searchList("Customers", "＋ Add customer", { customerForm(null) }) { q ->
        repo.customers.filter { q.isBlank() || "${it.p.name} ${it.p.phone} ${it.p.city}".contains(q, true) }.map { c ->
            ui.card(ui.text(c.p.name, 15f, true), ui.text(listOf(c.p.addr1, c.p.addr2, c.p.city, c.p.state, c.p.pin).filter { it.isNotBlank() }.joinToString(", "), 12f, false, ui.sub),
                ui.text("${c.p.phone}  ${c.p.email}", 12f, false, ui.sub),
                ui.row(ui.btn("Edit", false) { customerForm(c) }, ui.btn("Delete", false) { ui.confirm("Delete ${c.p.name}? Saved invoices are not affected.") { repo.customers.remove(c); repo.save(); customers() } }))
        }
    }

    private fun customerForm(c: Customer?) {
        val p = c?.p ?: Party()
        val n = ui.field("Customer name", p.name); val a1 = ui.field("Address line 1", p.addr1); val a2 = ui.field("Address line 2", p.addr2)
        val city = ui.field("City", p.city); val st = stateField(p.state); val pin = ui.field("PIN code", p.pin, ui.NUM)
        val ph = ui.field("Phone number", p.phone, ui.PHONE); val em = ui.field("Email ID", p.email, ui.EMAIL)
        ui.form(if (c == null) "Add customer" else "Edit customer", n, a1, a2, city, st, pin, ph, em) {
            val phv = ph.text.toString().trim(); val pv = pin.text.toString().trim(); val ev = em.text.toString().trim()
            when {
                n.text.isBlank() -> "Enter the customer name."
                pv.isNotEmpty() && pv.length != 6 -> "PIN code must be 6 digits."
                phv.isNotEmpty() && phv.filter { it.isDigit() }.length !in 7..15 -> "Enter a valid phone number."
                ev.isNotEmpty() && !ev.contains("@") -> "Enter a valid email address."
                else -> { val np = Party(n.text.toString().trim(), a1.text.toString().trim(), a2.text.toString().trim(), city.text.toString().trim(), st.text.toString().trim(), pv, phv, ev)
                    if (c == null) repo.customers.add(Customer(repo.newId(), np)) else c.p = np
                    repo.save(); customers(); null }
            }
        }
    }

    // ---------------- History ----------------
    private fun history(): Unit = searchList("Invoice History", "", {}) { q ->
        repo.invoices.sortedByDescending { it.createdAt }.filter { q.isBlank() || "${it.customer} ${it.orderNo} ${it.dateTime}".contains(q, true) }.map { r ->
            ui.card(ui.text(r.orderNo, 15f, true), ui.text("${r.customer} · ${r.dateTime}", 12f, false, ui.sub), ui.text(money.format(r.total.toBigDecimalOrNull() ?: BigDecimal.ZERO), 14f, true, ui.accent),
                ui.row(ui.btn("View / export", false) { inv.openSaved(r) }, ui.btn("Delete", false) { ui.confirm("Delete invoice ${r.orderNo} and its PDF?") { repo.invoices.remove(r); File(repo.pdfDir, "${r.orderNo}.pdf").delete(); repo.save(); history() } }))
        }
    }

    // ---------------- Store settings ----------------
    private fun storeSettings() {
        val s = repo.store; val t = repo.settings
        val n = ui.field("Store name", s.name); val a1 = ui.field("Address line 1", s.a1); val a2 = ui.field("Address line 2", s.a2); val a3 = ui.field("Address line 3", s.a3)
        val ph = ui.field("Phone number (use / for a second number)", s.phone, ui.PLAIN); val g = ui.field("GST number", s.gst, ui.PLAIN)
        val till = ui.field("Default till number", t.till, ui.NUM); val by = ui.field("Default 'Created by'", t.createdBy); val pay = ui.field("Default payment method", t.payment)
        val cb = CheckBox(this).apply { text = "Show tax amounts as rate × price (legacy system style) instead of extracting tax from the inclusive price"; isChecked = t.taxMode == "ORIGINAL" }
        show(ui.scroll(ui.col(ui.h2("Store details"), n, a1, a2, a3, ph, g, ui.h2("Invoice defaults"), till, by, pay, cb,
            ui.btn("Save settings") {
                if (n.text.isBlank() || till.text.isBlank() || by.text.isBlank() || pay.text.isBlank()) { ui.toast("Store name, till, created by and payment cannot be empty."); return@btn }
                repo.store = StoreInfo(n.text.toString().trim(), a1.text.toString().trim(), a2.text.toString().trim(), a3.text.toString().trim(), ph.text.toString().trim(), g.text.toString().trim())
                repo.settings = Settings(till.text.toString().trim(), by.text.toString().trim(), pay.text.toString().trim(), if (cb.isChecked) "ORIGINAL" else "INCLUSIVE")
                repo.save(); inv.reset(); ui.toast("Saved."); go("home")
            }.also { it.layoutParams = ui.lp(m = 8) })), "Store Settings")
    }

    // ---------------- Product types ----------------
    private fun types(): Unit = show(ui.scroll(ui.col(ui.text("HSN code and GST are stored per product type. HSN cannot be edited on an invoice.", 12f, false, ui.sub),
        *repo.types.map { t -> ui.card(ui.text(t.name, 15f, true), ui.text("HSN ${t.hsn} · CGST ${t.cgst}% · SGST ${t.sgst}%", 12f, false, ui.sub),
            ui.btn("Edit", false) {
                val h = ui.field("HSN code", t.hsn, ui.NUM); val c = ui.field("Default CGST %", t.cgst, ui.DEC); val s = ui.field("Default SGST %", t.sgst, ui.DEC)
                ui.form("Edit ${t.name}", h, c, s) {
                    if (h.text.isBlank() || c.text.toString().toBigDecimalOrNull() == null || s.text.toString().toBigDecimalOrNull() == null) "Enter HSN, CGST and SGST."
                    else { t.hsn = h.text.toString().trim(); t.cgst = c.text.toString().trim(); t.sgst = s.text.toString().trim(); repo.save(); types(); null }
                }
            }) }.toTypedArray())), "Product Types")

    // ---------------- Backup & restore ----------------
    private fun backup() {
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        repo.backups().forEach { f -> list.addView(ui.card(ui.text(f.name, 13f), ui.btn("Restore", false) {
            ui.confirm("Replace all current data with this backup? A safety backup of the current data is made first.") {
                val e = repo.importJson(f.readText(), false); ui.toast(e ?: "Restored."); backup() } })) }
        show(ui.scroll(ui.col(
            ui.btn("Export all data (JSON file)") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE, "store-invoice-backup.json"), 1) }.also { it.layoutParams = ui.lp(m = 6) },
            ui.btn("Import data from JSON file", false) { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 2) }.also { it.layoutParams = ui.lp(m = 6) },
            ui.btn("Create backup now", false) { repo.backup(); ui.toast("Backup created."); backup() }.also { it.layoutParams = ui.lp(m = 6) },
            ui.h2("Saved backups (on this device)"), list)), "Backup & Restore")
    }

    fun exportPdf(file: File, orderNo: String) {
        pendingFile = file
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf").putExtra(Intent.EXTRA_TITLE, "$orderNo.pdf"), 3)
    }

    @Deprecated("framework result API")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (res != RESULT_OK || uri == null) return
        try {
            when (req) {
                1 -> { contentResolver.openOutputStream(uri, "wt")!!.use { it.write(repo.toJson().toString(2).toByteArray()) }; ui.toast("Exported.") }
                3 -> { contentResolver.openOutputStream(uri, "wt")!!.use { o -> pendingFile!!.inputStream().use { it.copyTo(o) } }; ui.toast("PDF exported.") }
                2 -> {
                    val txt = contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
                    AlertDialog.Builder(this).setTitle("Import data").setMessage("Merge adds items that are missing and keeps everything you have. Replace swaps all data for the file's data. A safety backup is made first either way.")
                        .setPositiveButton("Merge") { _, _ -> ui.toast(repo.importJson(txt, true) ?: "Imported (merged).") ; go("backup") }
                        .setNeutralButton("Replace") { _, _ -> ui.toast(repo.importJson(txt, false) ?: "Imported (replaced)."); go("backup") }
                        .setNegativeButton("Cancel", null).show()
                }
            }
        } catch (e: Exception) { ui.toast("Failed: ${e.message}") }
    }
}
