package com.example.localqwen.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localqwen.memory.MemoryItem
import com.example.localqwen.memory.MemoryStore
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MemoryDialog(
    memories: List<MemoryItem>,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ذاكرة نبض", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "تُحفظ الذاكرة محليًا على جهازك فقط. لا تحفظ معلومات حساسة مثل كلمات المرور.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (memories.isEmpty()) {
                    Text("ذاكرة نبض فارغة.", modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(memories) { memory ->
                            MemoryCard(memory, onDelete, onEdit)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun MemoryCard(
    memory: MemoryItem,
    onDelete: (String) -> Unit,
    onEdit: (String, String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(memory.text) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isEditing) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { isEditing = false }) { Text("إلغاء") }
                    TextButton(onClick = {
                        onEdit(memory.id, editText)
                        isEditing = false
                    }) { Text("حفظ") }
                }
            } else {
                Text(memory.text, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${getCategoryLabel(memory.category)} • ${formatDate(memory.updatedAt)}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { isEditing = true }) {
                        Text("تعديل", color = Color(0xFFE65100), fontSize = 12.sp)
                    }
                    TextButton(onClick = { onDelete(memory.id) }) {
                        Text("حذف", color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ToolConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = NabdColors.Rose)) {
                Text("تأكيد", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("إلغاء") }
        }
    )
}

private fun getCategoryLabel(category: String): String {
    return when (category) {
        MemoryStore.CATEGORY_PREFERENCE -> "تفضيل"
        MemoryStore.CATEGORY_PROFILE -> "ملف شخصي"
        MemoryStore.CATEGORY_PROJECT -> "مشروع"
        else -> "عام"
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
