# Nabd v0.2.0 KV Cache Strategy

## Summary
Nabd currently uses a stable stateless-conversation strategy on top of LiteRT-LM.

The model Engine remains loaded in memory, while the Conversation object is reset before each new generation.

## Current Behavior
- The LiteRT Engine is not recreated for every message.
- A new Conversation is created before each generation.
- The previous Conversation is closed safely.
- KV Cache is cleared between user messages.
- KV Cache remains active during a single streaming response.

## Why This Is Used
Nabd builds the final prompt manually using:
- Recent chat history
- RAG document context
- Memory instructions
- Response mode instructions

Because RAG context can change with every question, resetting the Conversation prevents hidden context buildup and avoids mixing old internal cache with the new manual prompt.

## Trade-off
### Advantages
- More predictable responses
- Safer RAG behavior
- Lower risk of context bloat
- Better stability in long conversations

### Disadvantages
- Less reuse of KV Cache between separate user messages
- More prefill work for each new message

## Performance Notes
KV Cache still improves generation speed during streaming, after the prompt is processed.

Current optimization focuses on:
- Limiting chat history to recent messages
- Reducing document context size
- Response modes: fast, balanced, detailed
- Measuring TTFT and TPS

## Future Option
A future experimental mode may support:
- Stable mode: reset Conversation before each message
- Fast mode: keep Conversation alive and send only the new user message

This should only be implemented after careful testing, especially with RAG disabled or in text-only chats.

## Current Recommendation
Keep the current stable reset-before-generation strategy for v0.2.0-beta.

---

# استراتيجية إدارة الـ KV Cache وسياق المحادثة (v0.2.0)

يوضح هذا المستند كيفية تعامل محرك "نبض" مع ذاكرة المفاتيح والقيم (KV Cache) وإدارة سياق الحوار باستخدام مكتبة LiteRT-LM.

## 1. الوضع الحالي (النمط Stateless)

يعتمد تطبيق نبض حالياً على نمط **تصفير السياق الداخلي** لكل رسالة جديدة، مع إدارة الذاكرة يدوياً في طبقة التطبيق.

### أ. آلية العمل
- قبل كل عملية توليد، يتم استدعاء `engine.resetConversation()`.
- هذا الاستدعاء يغلق كائن `Conversation` القديم ويفتح واحداً جديداً.
- يتم مسح الـ **KV Cache** بالكامل من ذاكرة المحرك (GPU/RAM).

### ب. لماذا يتم تصفير الذاكرة؟
1. **التحكم الكامل (Manual Prompting):** يقوم التطبيق ببناء الـ Prompt يدوياً عبر دمج (نظام التعليمات + سياق المستندات المسترجع RAG + آخر 6 رسائل من التاريخ).
2. **تجنب تضخم السياق (Context Bloat):** لو تم الاحتفاظ بالـ KV Cache مع إرسال تاريخ كامل يدوياً، سيقوم المحرك بمضاعفة معالجة البيانات، مما يؤدي لامتلاء نافذة السياق بسرعة وانهيار الجودة.
3. **مرونة الـ RAG:** في كل سؤال، قد تتغير المعلومات المسترجعة من المستندات. التصفير يضمن أن النموذج يرى أحدث المعلومات فقط دون تشويش من سياق مستندات قديم.

## 2. تحليل الأداء

| الميزة | التأثير الحالي | الملاحظات |
| :--- | :--- | :--- |
| **سرعة الـ Pre-fill** | متوسطة | يضطر المحرك لإعادة معالجة (Process) كامل التاريخ في كل رسالة. |
| **استقرار الذاكرة** | مرتفع جداً | يتم تحرير الـ KV Cache دورياً، مما يمنع تراكم الاستهلاك. |
| **دقة الـ RAG** | مرتفعة | نضمن عدم تداخل معلومات المستندات بين الأسئلة المختلفة. |

## 3. التوصيات التقنية

### للمدى القريب (الإصدار v0.2.x):
- **الاستمرار على النهج الحالي:** التصفير اليدوي هو الأكثر أماناً للأجهزة ذات الذاكرة المحدودة (4GB - 6GB RAM).
- **تحسين التاريخ:** تم تحديد الحد الأقصى للتاريخ بـ 6 رسائل في `ChatViewModel` لضمان سرعة الـ Pre-fill.

### للمستقبل (إصدارات متقدمة):
- **النمط الهجين (Hybrid Mode):**
    - في المحادثات العادية: تعطيل التصفير وإرسال "الرسالة الجديدة فقط" لاستغلال سرعة الـ KV Cache.
    - في محادثات المستندات: تفعيل التصفير لضمان دقة الـ RAG.
- **تعديل `EngineConfig`:** استكشاف إمكانية تحديد `max_context_length` برمجياً بدلاً من الاعتماد على القيم الافتراضية للنموذج لتقليل استهلاك الذاكرة في الأجهزة الضعيفة.
