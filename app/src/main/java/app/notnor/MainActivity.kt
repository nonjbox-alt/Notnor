package app.notnor

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import java.util.Locale

private const val LIME = 0xFFC8FF59.toInt()
private const val BG = 0xFF10110F.toInt()
private const val PANEL = 0xFF1B1D19.toInt()
private const val MUTED = 0xFF9DA197.toInt()

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("notnor", MODE_PRIVATE) }
    private val media = mutableListOf<MediaItem>()
    private lateinit var list: LinearLayout
    private lateinit var query: EditText
    private lateinit var count: TextView
    private var filter = "all"

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.edit { putString("folder", uri.toString()) }
            scanFolder(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG; window.navigationBarColor = BG
        buildUi()
        val saved = prefs.getString("folder", null)
        if (saved != null) scanFolder(Uri.parse(saved)) else render()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(20), dp(16), dp(20), 0) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val logo = TextView(this).apply { text = "notnor"; textSize = 30f; setTextColor(LIME); typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD) }
        val spacer = View(this)
        val folder = button("+ إضافة مجلد", LIME, BG) { pickFolder.launch(null) }
        top.addView(logo); top.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f)); top.addView(folder)
        root.addView(top)
        root.addView(label("NOT NORMAL  /  مكتبتك، بطريقتك", MUTED, 11f).apply { setPadding(0, dp(3), 0, dp(18)) })
        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)); background = rounded(PANEL, 20) }
        hero.addView(label("مساء المشاهدة.", Color.WHITE, 24f, true))
        hero.addView(label("كل ملفاتك في مكان واحد.", MUTED, 14f).apply { setPadding(0, dp(5), 0, dp(14)) })
        hero.addView(button("اختيار مجلد الفيديوهات  ↗", LIME, BG) { pickFolder.launch(null) })
        root.addView(hero, LinearLayout.LayoutParams(-1, -2))
        root.addView(label("مكتبتك", Color.WHITE, 21f, true).apply { setPadding(0, dp(22), 0, dp(8)) })
        query = EditText(this).apply { hint = "ابحث عن فيلم أو حلقة…"; textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(MUTED); setSingleLine(); setPadding(dp(14), 0, dp(14), 0); background = rounded(PANEL, 14); addTextChangedListener(SimpleWatcher { render() }) }
        root.addView(query, LinearLayout.LayoutParams(-1, dp(48)))
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(8)) }
        listOf("الكل" to "all", "أكملها" to "done", "أكمل المشاهدة" to "resume").forEach { (title, key) ->
            val b = TextView(this).apply { text = title; textSize = 12f; gravity = Gravity.CENTER; setPadding(dp(12), dp(9), dp(12), dp(9)); setTextColor(if (filter == key) BG else MUTED); background = rounded(if (filter == key) LIME else PANEL, 30); setOnClickListener { filter = key; buildUiRefresh() } }
            filters.addView(b, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }
        root.addView(filters)
        count = label("", MUTED, 12f).apply { setPadding(0, 0, 0, dp(6)) }; root.addView(count)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(24)) }
        scroll.addView(list); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun buildUiRefresh() { buildUi(); prefs.getString("folder", null)?.let { scanFolder(Uri.parse(it)) } }

    private fun scanFolder(tree: Uri) {
        media.clear()
        try {
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            scanDocument(tree, rootId, 0)
        } catch (_: Exception) { Toast.makeText(this, "تعذّر قراءة المجلد. جرّب اختياره مرة أخرى.", Toast.LENGTH_LONG).show() }
        render()
    }

    private fun scanDocument(tree: Uri, documentId: String, depth: Int) {
        if (depth > 8 || media.size > 1500) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (c.moveToNext()) {
                val id = c.getString(idCol); val name = c.getString(nameCol) ?: "بدون اسم"; val mime = c.getString(mimeCol) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) scanDocument(tree, id, depth + 1)
                else if (mime.startsWith("video/") || name.substringAfterLast('.', "").lowercase(Locale.ROOT) in listOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts")) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    media.add(MediaItem(uri, name, Regex("(?i)s(\\d{1,2})e(\\d{1,2})").find(name)?.value?.uppercase(Locale.ROOT)))
                }
            }
        }
    }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val q = if (::query.isInitialized) query.text.toString().trim() else ""
        val items = media.filter { it.name.contains(q, true) && when (filter) { "done" -> isDone(it); "resume" -> !isDone(it) && getPosition(it) > 0; else -> true } }
        count.text = "${items.size} ملف${if (items.size == 1) "" else "ات"}"
        if (items.isEmpty()) {
            val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(16), dp(35), dp(16), dp(35)); background = rounded(PANEL, 18) }
            empty.addView(label(if (media.isEmpty()) "مكتبتك تنتظرك" else "ما لقينا نتيجة", Color.WHITE, 18f, true))
            empty.addView(label(if (media.isEmpty()) "أضف مجلدًا يحتوي على فيديوهاتك، وسأرتّبها لك هنا." else "جرّب كلمة بحث ثانية.", MUTED, 13f).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
            if (media.isEmpty()) empty.addView(button("اختيار مجلد", LIME, BG) { pickFolder.launch(null) }.apply { setPadding(dp(16), dp(10), dp(16), dp(10)) }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
            list.addView(empty); return
        }
        items.forEach { item ->
            val card = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)); background = rounded(PANEL, 16) }
            val play = TextView(this).apply { text = if (isDone(item)) "✓" else "▶"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(BG); background = rounded(LIME, 12) }
            card.addView(play, LinearLayout.LayoutParams(dp(44), dp(44)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
            info.addView(label(item.name, Color.WHITE, 14f, true))
            val sub = listOfNotNull(item.seasonEpisode, if (isDone(item)) "تمت المشاهدة" else if (getPosition(item) > 0) "متابعة المشاهدة" else "غير مُشاهَد").joinToString("  ·  ")
            info.addView(label(sub, MUTED, 11f).apply { setPadding(0, dp(4), 0, 0) })
            card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.setOnClickListener { startActivity(Intent(this, PlayerActivity::class.java).putExtra("uri", item.uri.toString()).putExtra("name", item.name).putExtra("key", item.uri.toString())) }
            val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(9); list.addView(card, lp)
        }
    }

    private fun isDone(item: MediaItem) = prefs.getBoolean("done_${item.uri}", false)
    private fun getPosition(item: MediaItem) = prefs.getLong("pos_${item.uri}", 0L)
    private fun label(text: String, color: Int, size: Float, bold: Boolean = false) = TextView(this).apply { this.text = text; setTextColor(color); textSize = size; if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
    private fun button(text: String, bg: Int, fg: Int, action: () -> Unit) = TextView(this).apply { this.text = text; gravity = Gravity.CENTER; textSize = 13f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(fg); setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(bg, 14); setOnClickListener { action() } }
    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
}