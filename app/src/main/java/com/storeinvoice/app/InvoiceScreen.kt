package com.storeinvoice.app

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class DItem(var name: String, var type: String, var code: String, var hsn: String, var cgst: BigDecimal, var sgst: BigDecimal, var qty: Int, var price: String)

class Draft(r: Repo) {
    var sName = r.store.name; var s1 = r.store.a1; var s2 = r.store.a2; var s3 = r.store.a3; var sPhone = r.store.phone; var gst = r.store.gst
    var till = r.settings.till; var orderNo = r.newOrderNo(); var createdBy = r.settings.createdBy; var dateTime = r.now()
    var pay = r.settings.payment; var last4 = r.digits(4); var txnRef = r.digits(10); var txnId = r.newTxnId()
    var legal = r.store.legal; var cin = r.store.cin; var regd1 = r.store.regd1; var regd2 = r.store.regd2; var regd3 = r.store.regd3
    var site = r.store.site; var email = r.store.email
    var cust = Party(); val items = ArrayList<DItem>()
}

class InvoiceScreen(private val act: MainActivity) {
    private val ui get() = act.ui
    private val repo get() = act.repo
    private var draft = Draft(act.repo)
    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun reset() { draft = Draft(repo) }

    /** Loads a saved invoice back into the form (to re-create a lost/deleted record or issue a similar one). */
    fun loadFrom(x: InvoiceData) {
        val d = Draft(repo)
        d.sName = x.storeName; d.s1 = x.sAddr1; d.s2 = x.sAddr2; d.s3 = x.sAddr3; d.sPhone = x.sPhone; d.gst = x.gst
        d.till = x.till; d.orderNo = x.orderNo; d.createdBy = x.createdBy; d.dateTime = x.dateTime; d.pay = x.payMethod
        d.last4 = x.last4; d.txnRef = x.txnRef; d.txnId = x.transactionId; d.cust = x.customer
        d.legal = x.legal; d.cin = x.cin; d.regd1 = x.regd1; d.regd2 = x.regd2; d.regd3 = x.regd3; d.site = x.site; d.email = x.email
        x.items.forEach { i -> d.items.add(DItem(i.name, i.type, i.code, i.hsn, i.cgst, i.sgst, i.qty, i.unit.toPlainString())) }
        draft = d
    }

    private fun bd(s: String) = s.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO

    private fun data(): InvoiceData? {
        val d = draft
        fun fail(m: String): InvoiceData? { ui.toast(m); return null }
        if (d.items.isEmpty()) return fail("Add at least one product.")
        for (i in d.items) {
            if (i.qty < 1) return fail("Quantity must be at least 1 (${i.name}).")
            val p = i.price.trim().toBigDecimalOrNull(); if (p == null || p.signum() < 0) return fail("Enter a valid unit price for ${i.name}.")
        }
        if (d.cust.name.isBlank()) return fail("Customer name is required.")
        if (!Regex("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}").matches(d.dateTime)) return fail("Date & time must look like 05/09/2025 19:38.")
        if (d.till.isBlank() || d.orderNo.isBlank() || d.createdBy.isBlank() || d.pay.isBlank()) return fail("Till no., created by and payment method cannot be empty.")
        if (d.txnId.isBlank()) return fail("Transaction ID cannot be empty.")
        if (!Regex("SOA\\d{12}").matches(d.orderNo)) return fail("Order number must be SOA followed by 12 digits.")
        return InvoiceData(d.sName, d.s1, d.s2, d.s3, d.sPhone, d.gst, d.till, d.orderNo, d.createdBy, d.dateTime, d.cust, d.pay, d.last4, d.txnRef, d.txnId,
            repo.settings.taxMode, d.items.map { Item(it.name, it.type, it.code, it.hsn, it.qty, bd(it.price), it.cgst, it.sgst) },
            d.legal, d.cin, d.regd1, d.regd2, d.regd3, d.site, d.email)
    }

    fun view(): View {
        val d = draft
        val totalsTv = ui.text("", 14f)
        val itemsBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        fun totals() {
            val items = d.items.map { Item(it.name, it.type, it.code, it.hsn, maxOf(it.qty, 0), bd(it.price), it.cgst, it.sgst) }
            val t = Calc.compute(items, repo.settings.taxMode)
            totalsTv.text = "Subtotal (taxable): ${money.format(t.subtotal)}\nCGST: ${money.format(t.cgst)}   SGST: ${money.format(t.sgst)}\nGrand total: ${money.format(t.grand)}"
        }
        fun items() {
            itemsBox.removeAllViews()
            d.items.forEach { it0 ->
                val qty = ui.field("Qty", it0.qty.toString(), ui.NUM) { s -> it0.qty = s.toIntOrNull() ?: 0; totals() }
                val price = ui.field("Unit price (this invoice)", it0.price, ui.DEC) { s -> it0.price = s; totals() }
                itemsBox.addView(ui.card(
                    ui.text(it0.name, 14f, true), ui.text("${it0.type} · ${it0.code} · HSN ${it0.hsn} · CGST ${it0.cgst.toPlainString()}% SGST ${it0.sgst.toPlainString()}%", 12f, false, ui.sub),
                    ui.row(qty.also { it.layoutParams = ui.lp(ui.dp(80), ViewGroup.LayoutParams.WRAP_CONTENT) }, price.also { it.layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) },
                        ui.btn("Remove", false) { d.items.remove(it0); items(); totals() })))
            }
            totals()
        }
        fun f(h: String, v: String, t: Int = ui.PLAIN, set: (String) -> Unit) = ui.field(h, v, t, set)

        val custFields = ArrayList<Pair<EditText, (Party) -> String>>()
        fun cf(h: String, get: (Party) -> String, set: (Party, String) -> Party, t: Int = ui.PLAIN): EditText {
            val e = f(h, get(d.cust), t) { s -> d.cust = set(d.cust, s) }; custFields.add(e to get); return e
        }
        val cName = cf("Customer name", { it.name }, { p, s -> p.copy(name = s) })
        val c1 = cf("Address line 1", { it.addr1 }, { p, s -> p.copy(addr1 = s) }); val c2 = cf("Address line 2", { it.addr2 }, { p, s -> p.copy(addr2 = s) })
        val cCity = cf("City", { it.city }, { p, s -> p.copy(city = s) })
        val cState = AutoCompleteTextView(act).apply {
            hint = "State"; setText(d.cust.state); setAdapter(ArrayAdapter(act, android.R.layout.simple_dropdown_item_1line, States.names)); threshold = 1; setSingleLine()
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { d.cust = d.cust.copy(state = s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        custFields.add(cState to { p: Party -> p.state })
        val cPin = cf("PIN code", { it.pin }, { p, s -> p.copy(pin = s) }, ui.NUM)
        val cPh = cf("Phone number", { it.phone }, { p, s -> p.copy(phone = s) }, ui.PHONE); val cMail = cf("Email ID", { it.email }, { p, s -> p.copy(email = s) }, ui.EMAIL)
        val names = listOf("— Walk-in / type below —") + repo.customers.map { it.p.name }
        val custSp = ui.spinner(names) { pos ->
            if (pos > 0) { d.cust = repo.customers[pos - 1].p.copy(); custFields.forEach { (e, g) -> e.setText(g(d.cust)) } }
        }

        val dt = f("Date & time", d.dateTime) { d.dateTime = it }.apply {
            isFocusable = false; setOnClickListener {
                val c = Calendar.getInstance()
                Regex("(\\d{2})/(\\d{2})/(\\d{4}) (\\d{2}):(\\d{2})").matchEntire(d.dateTime)?.let { m ->
                    c.set(m.groupValues[3].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[1].toInt(), m.groupValues[4].toInt(), m.groupValues[5].toInt()) }
                DatePickerDialog(act, { _, y, mo, da ->
                    TimePickerDialog(act, { _, h, mi -> setText(String.format(Locale.US, "%02d/%02d/%04d %02d:%02d", da, mo + 1, y, h, mi)) }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        val orderF = f("Order number (SOA + 12 digits)", d.orderNo, ui.PLAIN) { d.orderNo = it.trim() }

        val addBtn = ui.btn("＋ Add product") { pickProduct { p ->
            val t = repo.typeOf(p.type)
            val ex = d.items.firstOrNull { it.code == p.code }
            if (ex != null) ex.qty++ else d.items.add(DItem(p.name, p.type, p.code, t?.hsn ?: "", bd(p.cgst), bd(p.sgst), 1, p.price))
            items()
        } }
        items()
        val tools = ui.row(
            ui.btn("Preview", false) { data()?.let { x -> val fl = File(act.cacheDir, "preview_${x.orderNo}.pdf"); PdfExport.write(act, x, fl); showPdf(fl, x.orderNo, null) } }.also { it.layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, 4) },
            ui.btn("Generate & Save PDF") {
                val x = data() ?: return@btn
                if (repo.invoices.any { it.orderNo == x.orderNo }) { ui.toast("Order number already used – tap Regenerate."); return@btn }
                try {
                    val fl = File(repo.pdfDir, "${x.orderNo}.pdf"); PdfExport.write(act, x, fl)
                    repo.invoices.add(InvRec(x.orderNo, System.currentTimeMillis(), x.dateTime, x.customer.name, Calc.compute(x.items, x.taxMode).grand.toPlainString(), x))
                    repo.save(); reset(); showPdf(fl, x.orderNo) { act.go("history") }
                } catch (e: Exception) { ui.toast("Could not save invoice: ${e.message}") }
            }.also { it.layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, 4) })

        return ui.scroll(ui.col(
            ui.h2("Customer"), ui.text("Saved customer", 12f, true, ui.sub), custSp, cName, c1, c2, cCity, cState, cPin, cPh, cMail,
            ui.h2("Invoice details"), f("Till number", d.till) { d.till = it },
            orderF, ui.btn("Generate new order number", false) { orderF.setText(repo.newOrderNo()) },
            f("Created by", d.createdBy) { d.createdBy = it }, dt, f("Payment method", d.pay) { d.pay = it },
            f("Last four digits", d.last4, ui.NUM) { d.last4 = it }, f("Transaction reference", d.txnRef, ui.NUM) { d.txnRef = it },
            f("Transaction ID (barcode)", d.txnId) { d.txnId = it },
            ui.h2("Store (this invoice only)"), f("Company line (e.g. Falah Retail Limited Trading as)", d.legal) { d.legal = it }, f("Store name", d.sName) { d.sName = it }, f("Address line 1", d.s1) { d.s1 = it }, f("Address line 2", d.s2) { d.s2 = it },
            f("Address line 3", d.s3) { d.s3 = it }, f("Phone number", d.sPhone) { d.sPhone = it }, f("GST number", d.gst) { d.gst = it }, f("CIN number", d.cin) { d.cin = it },
            f("Regd. office line 1", d.regd1) { d.regd1 = it }, f("Regd. office line 2", d.regd2) { d.regd2 = it }, f("Regd. office line 3", d.regd3) { d.regd3 = it },
            f("Website / store brand name (e.g. falah.com)", d.site) { d.site = it }, f("Support email", d.email, ui.EMAIL) { d.email = it },
            ui.h2("Products"), itemsBox, addBtn, ui.h2("Totals"), totalsTv, tools))
    }

    private fun pickProduct(onPick: (Product) -> Unit) {
        val shown = ArrayList<Product>(); val adapter = ArrayAdapter<String>(act, android.R.layout.simple_list_item_1)
        val lv = ListView(act); lv.adapter = adapter
        fun filter(q: String) {
            shown.clear(); shown.addAll(repo.products.filter { q.isBlank() || "${it.name} ${it.type} ${it.code}".contains(q, true) })
            adapter.clear(); adapter.addAll(shown.map { "${it.name}\n${it.type} · ${it.code} · ${money.format(bd(it.price))}" }); adapter.notifyDataSetChanged()
        }
        val search = ui.field("Search products", "", ui.PLAIN) { filter(it) }; filter("")
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.dp(12), ui.dp(8), ui.dp(12), 0); addView(search); addView(lv, ui.lp(h = ui.dp(320))) }
        val dlg = AlertDialog.Builder(act).setTitle("Select product").setView(box).setNegativeButton("Close", null).create()
        lv.setOnItemClickListener { _, _, pos, _ -> onPick(shown[pos]); dlg.dismiss() }
        if (repo.products.isEmpty()) ui.toast("No products yet – add them under Products.")
        dlg.show()
    }

    /** Full-screen PDF preview with share / export / open actions. Also used by invoice history. */
    fun showPdf(file: File, orderNo: String, onClose: (() -> Unit)?) {
        val pages = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#CBD2D9")); setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)) }
        try { PdfExport.render(file).forEach { b -> pages.addView(ImageView(act).apply { setImageBitmap(b); adjustViewBounds = true; setBackgroundColor(Color.WHITE) }, ui.lp(m = 4)) } }
        catch (e: Exception) { pages.addView(ui.text("Preview unavailable: ${e.message}")) }
        val dlg = Dialog(act, android.R.style.Theme_Material_Light_NoActionBar)
        fun uri() = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", file)
        val bar = ui.row(
            ui.btn("Share", false) { try { act.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share invoice")) } catch (e: Exception) { ui.toast(e.message ?: "Share failed") } },
            ui.btn("Export", false) { act.exportPdf(file, orderNo) },
            ui.btn("Open", false) { try { act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(), "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } catch (e: Exception) { ui.toast("No PDF viewer installed.") } },
            ui.btn("Close") { dlg.dismiss() })
        for (i in 0 until bar.childCount) bar.getChildAt(i).layoutParams = ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, 2)
        dlg.setContentView(LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; addView(ui.scroll(pages), ui.lp(h = 0, wt = 1f)); addView(bar) })
        dlg.setOnDismissListener { onClose?.invoke() }
        dlg.show()
    }
    fun showPdf(file: File, orderNo: String) = showPdf(file, orderNo, null)

    /** Re-opens a saved invoice; the PDF is rebuilt from the stored snapshot if the file was removed. */
    fun openSaved(r: InvRec) {
        val fl = File(repo.pdfDir, "${r.orderNo}.pdf")
        try { if (!fl.exists()) PdfExport.write(act, r.data, fl) } catch (e: Exception) { ui.toast("Could not build PDF: ${e.message}"); return }
        showPdf(fl, r.orderNo, null)
    }
}
