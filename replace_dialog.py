import re

with open("app/src/main/java/com/example/localqwen/MainActivity.kt", "r", encoding="utf-8") as f:
    content = f.read()

old_func = r"""    private fun showChatHistoryDialog\(\) \{
        scope\.launch \{
            val sessions = withContext\(Dispatchers\.IO\) \{ chatSessionStore\.getAllSessions\(\) \}
            if \(sessions\.isEmpty\(\)\) \{
                Toast\.makeText\(this@MainActivity, "لا يوجد سجل", Toast\.LENGTH_SHORT\)\.show\(\)
                return@launch
            \}

            MaterialAlertDialogBuilder\(this@MainActivity\)
                \.setTitle\("السجل"\)
                \.setItems\(sessions\.map \{ it\.title \}\.toTypedArray\(\)\) \{ _, which ->
                    switchSession\(sessions\[which\]\.id\)
                \}
                \.show\(\)
        \}
    \}"""

new_func = """    private fun showChatHistoryDialog() {
        scope.launch {
            val sessions = withContext(Dispatchers.IO) { chatSessionStore.getAllSessions().toMutableList() }
            if (sessions.isEmpty()) {
                Toast.makeText(this@MainActivity, "لا يوجد سجل", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val context = this@MainActivity
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(40, 40, 40, 40)
                setBackgroundColor(Color.parseColor("#202020"))
            }

            val searchInput = EditText(context).apply {
                hint = "ابحث في المحادثات..."
                setHintTextColor(Color.parseColor("#A0A0A0"))
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(context, R.drawable.bg_input)
                setPadding(30, 30, 30, 30)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 40) }
            }
            layout.addView(searchInput)

            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            val resultsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            scrollView.addView(resultsContainer)
            layout.addView(scrollView)

            val noResultsText = TextView(context).apply {
                text = "لا توجد نتائج مطابقة"
                setTextColor(Color.parseColor("#A0A0A0"))
                gravity = android.view.Gravity.CENTER
                visibility = View.GONE
                setPadding(0, 40, 0, 40)
            }
            layout.addView(noResultsText)

            val dialog = MaterialAlertDialogBuilder(context)
                .setView(layout)
                .show()
                
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#202020")))
            dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, (context.resources.displayMetrics.heightPixels * 0.8).toInt())

            fun renderSessions(query: String) {
                resultsContainer.removeAllViews()
                val q = query.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")
                var matchCount = 0

                val currentSessionId = chatSessionStore.getActiveSessionId()

                for (session in sessions) {
                    val messagesText = java.lang.StringBuilder()
                    try {
                        val arr = JSONArray(session.messagesJson)
                        for (i in 0 until arr.length()) {
                            messagesText.append(arr.getJSONObject(i).optString("text", "")).append(" ")
                        }
                    } catch (e: Exception) {}
                    val allText = messagesText.toString()
                    val searchableText = (session.title + " " + allText + " " + session.lastAssistantResponse).lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ")

                    if (q.isEmpty() || searchableText.contains(q)) {
                        matchCount++
                        val row = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(30, 30, 30, 30)
                            setBackgroundColor(Color.parseColor("#242424"))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 0, 0, 20) }
                            
                            var isPressed = false
                            setOnTouchListener { v, event ->
                                when (event.action) {
                                    android.view.MotionEvent.ACTION_DOWN -> { v.setBackgroundColor(Color.parseColor("#333333")); isPressed = true }
                                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { v.setBackgroundColor(Color.parseColor("#242424")); isPressed = false }
                                }
                                false
                            }

                            setOnClickListener {
                                dialog.dismiss()
                                switchSession(session.id)
                            }
                            setOnLongClickListener {
                                val options = arrayOf("فتح", "إعادة تسمية", "حذف", "نسخ المحادثة")
                                MaterialAlertDialogBuilder(context)
                                    .setItems(options) { _, which ->
                                        when (which) {
                                            0 -> { dialog.dismiss(); switchSession(session.id) }
                                            1 -> renameSessionPrompt(session) { renderSessions(searchInput.text.toString()) }
                                            2 -> deleteSessionPrompt(session) {
                                                sessions.removeAll { it.id == session.id }
                                                if(sessions.isEmpty()) dialog.dismiss() else renderSessions(searchInput.text.toString())
                                            }
                                            3 -> copySessionText(session)
                                        }
                                    }.show()
                                true
                            }
                        }

                        val titleLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                        val titleView = TextView(context).apply {
                            text = session.title
                            setTextColor(Color.WHITE)
                            textSize = 16f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        titleLayout.addView(titleView)

                        if (session.id == currentSessionId) {
                            val currentLabel = TextView(context).apply {
                                text = "الحالية"
                                setTextColor(Color.parseColor("#FF7000"))
                                textSize = 12f
                                setPadding(10, 0, 10, 0)
                            }
                            titleLayout.addView(currentLabel)
                        }
                        row.addView(titleLayout)
                        
                        val dateView = TextView(context).apply {
                            text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(session.updatedAt))
                            setTextColor(Color.parseColor("#A0A0A0"))
                            textSize = 12f
                            setPadding(0, 5, 0, 10)
                        }
                        row.addView(dateView)

                        var preview = ""
                        if (q.isNotEmpty() && allText.lowercase(Locale.getDefault()).contains(q)) {
                            val idx = allText.lowercase(Locale.getDefault()).indexOf(q)
                            val start = Math.max(0, idx - 40)
                            val end = Math.min(allText.length, idx + q.length + 40)
                            preview = "..." + allText.substring(start, end).replace("\\n", " ") + "..."
                        } else if (allText.isNotBlank()) {
                            preview = allText.take(80).replace("\\n", " ") + if (allText.length > 80) "..." else ""
                        }

                        if (preview.isNotEmpty()) {
                            val previewView = TextView(context).apply {
                                text = preview
                                setTextColor(Color.parseColor("#A0A0A0"))
                                textSize = 14f
                                maxLines = 2
                                ellipsize = android.text.TextUtils.TruncateAt.END
                            }
                            row.addView(previewView)
                        }

                        resultsContainer.addView(row)
                    }
                }
                noResultsText.visibility = if (matchCount == 0) View.VISIBLE else View.GONE
            }

            searchInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    renderSessions(s?.toString() ?: "")
                }
            })

            renderSessions("")
        }
    }

    private fun renameSessionPrompt(session: ChatSession, onRenamed: () -> Unit) {
        val input = EditText(this).apply {
            setText(session.title)
            setTextColor(Color.WHITE)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("إعادة تسمية")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    chatSessionStore.renameSession(session.id, newTitle)
                    session.title = newTitle
                    onRenamed()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun deleteSessionPrompt(session: ChatSession, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف المحادثة؟")
            .setMessage("هل أنت متأكد من حذف هذه المحادثة؟")
            .setPositiveButton("حذف") { _, _ ->
                chatSessionStore.deleteSession(session.id)
                if (chatSessionStore.getActiveSessionId() == session.id) {
                    chatSessionStore.setActiveSessionId(null)
                    val sessions = chatSessionStore.getAllSessions()
                    if (sessions.isNotEmpty()) {
                        switchSession(sessions.first().id)
                    } else {
                        chatMessages.clear()
                        chatAdapter.notifyDataSetChanged()
                    }
                }
                onDeleted()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun copySessionText(session: ChatSession) {
        val sb = java.lang.StringBuilder()
        try {
            val arr = JSONArray(session.messagesJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val role = obj.optString("role", "")
                val text = obj.optString("text", "")
                val name = if (role == "user") "أنت" else if (role == "assistant") "الذكاء الاصطناعي" else "النظام"
                sb.append("$name: $text\\n\\n")
            }
        } catch (e: Exception) {}
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Chat Session", sb.toString().trim())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "تم نسخ المحادثة", Toast.LENGTH_SHORT).show()
    }"""

if re.search(old_func, content):
    new_content = re.sub(old_func, new_func, content, count=1)
    with open("app/src/main/java/com/example/localqwen/MainActivity.kt", "w", encoding="utf-8") as f:
        f.write(new_content)
    print("Replaced successfully!")
else:
    print("Match not found!")
