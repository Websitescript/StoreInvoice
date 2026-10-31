package com.storeinvoice.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL

/** Small programmatic-UI toolkit so the app needs no layout XML and no UI libraries. */
class Ui(val a: Activity) {
    val density = a.resources.displayMetrics.density
    fun dp(i: Int) = (i * density).toInt()
    val ink = Color.parseColor("#1F2933"); val sub = Color.parseColor("#6B7785"); val accent = Color.parseColor("#0F766E")
    private val cache = object : LruCache<String, Bitmap>(24) {}
    private val main = Handler(Looper.getMainLooper())

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, wt: Float = 0f, m: Int = 0) =
        LinearLayout.LayoutParams(w, h, wt).also { it.setMargins(dp(m), dp(m), dp(m), dp(m)) }

    fun text(s: String, size: Float = 14f, bold: Boolean = false, color: Int = ink): TextView = TextView(a).apply {
        text = s; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    fun h2(s: String) = text(s, 16f, true, accent).also { it.setPadding(0, dp(14), 0, dp(4)) }

    fun btn(label: String, fill: Boolean = true, onClick: () -> Unit): Button = Button(a).apply {
        text = label; isAllCaps = false; setTextColor(if (fill) Color.WHITE else accent)
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); if (fill) setColor(accent) else { setColor(Color.WHITE); setStroke(dp(1), accent) } }
        setOnClickListener { onClick() }
    }

    fun field(hint: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, onChange: ((String) -> Unit)? = null): EditText =
        EditText(a).apply {
            this.hint = hint; setText(value); inputType = type; setSingleLine(type and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
            if (onChange != null) addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { onChange(s.toString()) }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
    val NUM = InputType.TYPE_CLASS_NUMBER
    val DEC = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    val PLAIN = InputType.TYPE_CLASS_TEXT
    val EMAIL = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    val PHONE = InputType.TYPE_CLASS_PHONE
    val URI = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI

    private fun label(v: View): TextView? {
        val h = (v as? EditText)?.hint?.toString()
        return if (h.isNullOrEmpty()) null else text(h, 12f, true, sub).also { it.setPadding(dp(4), dp(8), 0, 0) }
    }
    /** Vertical stack; every text field automatically gets its label above it. */
    fun col(vararg v: View, pad: Int = 16): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        v.forEach { c -> label(c)?.let { addView(it) }; addView(c) }
    }
    fun row(vararg v: View): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        v.forEach { c ->
            val l = label(c)
            if (l == null) addView(c) else {
                val w = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; layoutParams = c.layoutParams ?: lp(ViewGroup.LayoutParams.WRAP_CONTENT) }
                c.layoutParams = lp(); w.addView(l); w.addView(c); addView(w)
            }
        }
    }
    fun card(vararg v: View): LinearLayout = col(*v, pad = 12).apply {
        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(Color.WHITE); setStroke(dp(1), Color.parseColor("#D9E0E6")) }
        layoutParams = lp(m = 6)
    }
    fun scroll(v: View) = ScrollView(a).apply { addView(v); isFillViewport = true }
    fun toast(s: String) = Toast.makeText(a, s, Toast.LENGTH_LONG).show()
    fun spinner(items: List<String>, sel: Int = 0, onPick: (Int) -> Unit): Spinner = Spinner(a).apply {
        adapter = ArrayAdapter(a, android.R.layout.simple_spinner_dropdown_item, items); setSelection(sel)
        var first = true
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (first) { first = false; return }; onPick(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }
    fun confirm(msg: String, yes: () -> Unit) { AlertDialog.Builder(a).setMessage(msg).setPositiveButton("Yes") { _, _ -> yes() }.setNegativeButton("Cancel", null).show() }

    /** Form dialog; onSave returns an error message (dialog stays open) or null (dialog closes). */
    fun form(title: String, vararg v: View, onSave: () -> String?) {
        val d = AlertDialog.Builder(a).setTitle(title).setView(scroll(col(*v))).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        d.show()
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val e = onSave(); if (e == null) d.dismiss() else toast(e) }
    }

    fun loadImage(url: String, iv: ImageView) {
        iv.setImageDrawable(null)
        if (url.isBlank()) return
        cache.get(url)?.let { iv.setImageBitmap(it); return }
        Thread {
            try {
                val c = URL(url).openConnection() as HttpURLConnection; c.connectTimeout = 6000; c.readTimeout = 8000
                val bmp = BitmapFactory.decodeStream(c.inputStream)
                if (bmp != null) { cache.put(url, bmp); main.post { iv.setImageBitmap(bmp) } }
            } catch (e: Exception) { /* offline or bad URL: leave blank */ }
        }.start()
    }
}
