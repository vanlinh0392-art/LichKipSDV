package com.sdv.lichnoti

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class NoteActivity : AppCompatActivity() {
    private lateinit var noteDate: LocalDate
    private lateinit var repository: DayNoteRepository
    private lateinit var editor: RichNoteEditText
    private lateinit var viewer: TextView
    private lateinit var viewerScroll: ScrollView
    private lateinit var progress: ProgressBar
    private lateinit var saveStatus: TextView
    private lateinit var editButton: MaterialButton
    private lateinit var deleteButton: ImageButton
    private lateinit var formattingBar: View
    private lateinit var boldButton: MaterialButton
    private lateinit var italicButton: MaterialButton

    private val handler = Handler(Looper.getMainLooper())
    private var mode = MODE_VIEW
    private var loaded = false
    private var noteExists = false
    private var dirty = false
    private var suppressChanges = false
    private var formattingMutation = false
    private var typingBold = false
    private var typingItalic = false
    private var saveGeneration = 0
    private var saveInFlight = false
    private var closeWhenSaved = false

    private val autosaveRunnable = Runnable { saveNow(finishAfter = false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_note)

        val rawDate = intent.getStringExtra(EXTRA_DATE)
        noteDate = runCatching { LocalDate.parse(rawDate) }.getOrElse {
            finish()
            return
        }
        mode = intent.getStringExtra(EXTRA_MODE).takeIf { it == MODE_EDIT } ?: MODE_VIEW
        repository = DayNoteRepository.getInstance(applicationContext)

        bindViews()
        bindActions()
        bindEditor()
        loadNote()
    }

    private fun bindViews() {
        editor = findViewById(R.id.etNoteContent)
        viewer = findViewById(R.id.tvNoteContent)
        viewerScroll = findViewById(R.id.noteViewerScroll)
        progress = findViewById(R.id.progressNote)
        saveStatus = findViewById(R.id.tvNoteSaveStatus)
        editButton = findViewById(R.id.btnEditNote)
        deleteButton = findViewById(R.id.btnDeleteNote)
        formattingBar = findViewById(R.id.layoutNoteFormatting)
        boldButton = findViewById(R.id.btnNoteBold)
        italicButton = findViewById(R.id.btnNoteItalic)

        val formatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale("vi", "VN"))
        val title = noteDate.format(formatter).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale("vi", "VN")) else it.toString()
        }
        findViewById<TextView>(R.id.tvNoteDate).text = title
    }

    private fun bindActions() {
        findViewById<View>(R.id.btnCloseNote).setOnClickListener { flushAndFinish() }
        editButton.setOnClickListener { showMode(MODE_EDIT, requestKeyboard = true) }
        deleteButton.setOnClickListener { confirmDelete() }
        boldButton.setOnClickListener { toggleStyle(Typeface.BOLD) }
        italicButton.setOnClickListener { toggleStyle(Typeface.ITALIC) }
    }

    private fun bindEditor() {
        editor.onSelectionChangedListener = { start, end ->
            if (loaded && !formattingMutation) {
                val text = editor.text
                if (text != null) {
                    if (start == end) {
                        typingBold = hasStyleAt(text, start, Typeface.BOLD)
                        typingItalic = hasStyleAt(text, start, Typeface.ITALIC)
                    }
                    updateFormatButtons(start, end)
                }
            }
        }
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (suppressChanges || formattingMutation || mode != MODE_EDIT || count <= 0) return
                val text = editor.text ?: return
                formattingMutation = true
                if (typingBold) text.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    start + count,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (typingItalic) text.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    start,
                    start + count,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                formattingMutation = false
            }

            override fun afterTextChanged(s: Editable?) {
                if (!suppressChanges && !formattingMutation && mode == MODE_EDIT) markDirty()
            }
        })
    }

    private fun loadNote() {
        progress.visibility = View.VISIBLE
        repository.getNote(noteDate) { result ->
            if (isFinishing || isDestroyed) return@getNote
            result.onFailure {
                Toast.makeText(this, "Không thể mở ghi chú", Toast.LENGTH_LONG).show()
                finish()
            }.onSuccess { note ->
                noteExists = note != null
                val content = note?.let { NoteMarkupCodec.decode(it.html) }
                    ?: SpannableStringBuilder()
                suppressChanges = true
                editor.setText(content, TextView.BufferType.SPANNABLE)
                viewer.text = content
                suppressChanges = false
                loaded = true
                dirty = false
                progress.visibility = View.GONE
                showMode(mode, requestKeyboard = mode == MODE_EDIT)
                setResultState(noteExists)
            }
        }
    }

    private fun showMode(newMode: String, requestKeyboard: Boolean) {
        mode = newMode
        val editing = newMode == MODE_EDIT
        editor.visibility = if (editing) View.VISIBLE else View.GONE
        viewerScroll.visibility = if (editing) View.GONE else View.VISIBLE
        formattingBar.visibility = if (editing) View.VISIBLE else View.GONE
        saveStatus.visibility = if (editing) View.VISIBLE else View.GONE
        editButton.visibility = if (editing) View.GONE else View.VISIBLE
        deleteButton.visibility = if (editing && noteExists) View.VISIBLE else View.GONE
        if (editing) {
            saveStatus.text = if (dirty || saveInFlight) "Đang lưu…" else "Đã lưu"
            editor.setSelection(editor.text?.length ?: 0)
            updateFormatButtons(editor.selectionStart, editor.selectionEnd)
            if (requestKeyboard) {
                editor.post {
                    editor.requestFocus()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        } else {
            viewer.text = SpannableStringBuilder(editor.text ?: "")
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(editor.windowToken, 0)
        }
    }

    private fun markDirty() {
        if (!loaded) return
        dirty = true
        saveStatus.text = "Đang lưu…"
        handler.removeCallbacks(autosaveRunnable)
        handler.postDelayed(autosaveRunnable, AUTOSAVE_DELAY_MS)
    }

    private fun saveNow(finishAfter: Boolean) {
        handler.removeCallbacks(autosaveRunnable)
        if (!loaded || mode != MODE_EDIT) {
            if (finishAfter) finishWithResult()
            return
        }
        if (!dirty) {
            if (finishAfter) {
                if (saveInFlight) closeWhenSaved = true else finishWithResult()
            }
            return
        }

        val editable = editor.text ?: SpannableStringBuilder()
        val html = NoteMarkupCodec.encode(editable)
        val plainText = editable.toString()
        val generation = ++saveGeneration
        dirty = false
        saveInFlight = true
        closeWhenSaved = closeWhenSaved || finishAfter
        saveStatus.text = "Đang lưu…"

        repository.save(noteDate, html, plainText) { result ->
            if (generation != saveGeneration || isDestroyed) return@save
            saveInFlight = false
            result.onFailure {
                dirty = true
                closeWhenSaved = false
                saveStatus.text = "Lưu thất bại"
                Toast.makeText(this, "Không thể lưu ghi chú", Toast.LENGTH_LONG).show()
            }.onSuccess { hasNote ->
                noteExists = hasNote
                deleteButton.visibility = if (mode == MODE_EDIT && hasNote) View.VISIBLE else View.GONE
                setResultState(hasNote)
                saveStatus.text = if (dirty) "Đang lưu…" else "Đã lưu"
                if (closeWhenSaved && !dirty) finishWithResult()
            }
        }
    }

    private fun flushAndFinish() {
        if (!loaded || mode != MODE_EDIT) {
            finishWithResult()
            return
        }
        closeWhenSaved = true
        saveNow(finishAfter = true)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Xóa ghi chú?")
            .setMessage("Ghi chú của ngày này sẽ bị xóa vĩnh viễn.")
            .setPositiveButton("Xóa") { _, _ -> deleteNote() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun deleteNote() {
        handler.removeCallbacks(autosaveRunnable)
        ++saveGeneration
        dirty = false
        saveInFlight = true
        closeWhenSaved = true
        saveStatus.text = "Đang xóa…"
        repository.delete(noteDate) { result ->
            if (isDestroyed) return@delete
            saveInFlight = false
            result.onFailure {
                closeWhenSaved = false
                saveStatus.text = "Xóa thất bại"
                Toast.makeText(this, "Không thể xóa ghi chú", Toast.LENGTH_LONG).show()
            }.onSuccess {
                noteExists = false
                setResultState(false)
                finishWithResult()
            }
        }
    }

    private fun toggleStyle(targetStyle: Int) {
        val text = editor.text ?: return
        val start = min(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = max(editor.selectionStart, editor.selectionEnd).coerceAtMost(text.length)
        if (start == end) {
            if (targetStyle == Typeface.BOLD) typingBold = !typingBold
            if (targetStyle == Typeface.ITALIC) typingItalic = !typingItalic
            updateFormatButtons(start, end)
            return
        }

        val enable = !isRangeStyled(text, start, end, targetStyle)
        formattingMutation = true
        setStyleOnRange(text, start, end, targetStyle, enable)
        formattingMutation = false
        editor.setSelection(start, end)
        markDirty()
        updateFormatButtons(start, end)
    }

    private fun updateFormatButtons(start: Int, end: Int) {
        val text = editor.text ?: return
        val bold = if (start == end) typingBold else isRangeStyled(text, start, end, Typeface.BOLD)
        val italic = if (start == end) typingItalic else isRangeStyled(text, start, end, Typeface.ITALIC)
        applyFormatButtonState(boldButton, bold)
        applyFormatButtonState(italicButton, italic)
    }

    private fun applyFormatButtonState(button: MaterialButton, active: Boolean) {
        val primary = ContextCompat.getColor(this, R.color.primary)
        val surface = ContextCompat.getColor(this, R.color.surface)
        button.backgroundTintList = ColorStateList.valueOf(
            if (active) ColorUtils.setAlphaComponent(primary, 36) else surface
        )
        button.strokeColor = ColorStateList.valueOf(
            if (active) primary else ContextCompat.getColor(this, R.color.divider)
        )
        button.setTextColor(if (active) primary else ContextCompat.getColor(this, R.color.on_surface))
        button.isActivated = active
    }

    private fun hasStyleAt(text: Spannable, position: Int, targetStyle: Int): Boolean {
        if (text.isEmpty()) return false
        val probe = if (position > 0) position - 1 else 0
        return text.getSpans(probe, min(probe + 1, text.length), StyleSpan::class.java)
            .any { it.style and targetStyle != 0 }
    }

    private fun isRangeStyled(text: Spannable, start: Int, end: Int, targetStyle: Int): Boolean {
        if (start >= end) return false
        for (index in start until end) {
            val styled = text.getSpans(index, index + 1, StyleSpan::class.java)
                .any { it.style and targetStyle != 0 }
            if (!styled) return false
        }
        return true
    }

    private fun setStyleOnRange(
        text: Spannable,
        start: Int,
        end: Int,
        targetStyle: Int,
        enabled: Boolean
    ) {
        val spans = text.getSpans(start, end, StyleSpan::class.java).toList()
        spans.forEach { span ->
            val spanStart = text.getSpanStart(span)
            val spanEnd = text.getSpanEnd(span)
            val spanFlags = text.getSpanFlags(span)
            val originalStyle = span.style
            text.removeSpan(span)

            addStyleSpan(text, spanStart, min(spanEnd, start), originalStyle, spanFlags)
            val overlapStart = max(spanStart, start)
            val overlapEnd = min(spanEnd, end)
            val remainingStyle = originalStyle and targetStyle.inv()
            addStyleSpan(text, overlapStart, overlapEnd, remainingStyle, spanFlags)
            addStyleSpan(text, max(spanStart, end), spanEnd, originalStyle, spanFlags)
        }
        if (enabled) {
            text.setSpan(StyleSpan(targetStyle), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun addStyleSpan(text: Spannable, start: Int, end: Int, style: Int, flags: Int) {
        if (start < end && style != Typeface.NORMAL) {
            text.setSpan(StyleSpan(style), start, end, flags)
        }
    }

    private fun setResultState(hasNote: Boolean) {
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_DATE, noteDate.toString())
            putExtra(EXTRA_HAS_NOTE, hasNote)
        })
    }

    private fun finishWithResult() {
        setResultState(noteExists)
        finish()
    }

    override fun onPause() {
        if (!isFinishing && mode == MODE_EDIT && dirty) saveNow(finishAfter = false)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autosaveRunnable)
        super.onDestroy()
    }

    @android.annotation.SuppressLint("MissingSuperCall")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        flushAndFinish()
    }

    companion object {
        const val EXTRA_DATE = "note_date"
        const val EXTRA_MODE = "note_mode"
        const val EXTRA_HAS_NOTE = "note_has_content"
        const val MODE_VIEW = "view"
        const val MODE_EDIT = "edit"
        private const val AUTOSAVE_DELAY_MS = 600L
    }
}
