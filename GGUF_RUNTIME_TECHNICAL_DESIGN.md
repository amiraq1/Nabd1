# التصميم التقني لمحرك GGUF التجريبي - GGUF Runtime Technical Design (v0.6.0)

يوضح هذا المستند البنية البرمجية المقترحة لدمج محركات تشغيل متعددة (Multi-Runtime) داخل تطبيق "نبض"، مع التركيز على توفير واجهة موحدة تدعم محرك LiteRT الحالي ومحرك GGUF التجريبي مستقبلاً.

## 1. المبادئ التصميمية الأساسية
- **التجريد (Abstraction):** فصل منطق المحادثة عن تفاصيل محرك التشغيل.
- **الأمان (Safety):** بقاء Qwen كنموذج افتراضي وحيد للمستخدم النهائي.
- **العزل (Isolation):** حصر تجارب GGUF داخل "واجهة المطور" ومنع تأثيرها على ثبات التطبيق.
- **المرونة (Flexibility):** سهولة إضافة أو إزالة محركات تشغيل جديدة دون تعديل الكود الأساسي.

## 2. الواجهات البرمجية المقترحة (Core Interfaces)

تم تصميم المكونات التالية نظرياً بلغة Kotlin لتمثيل دورة حياة المحرك وعملية التوليد:

```kotlin
/**
 * الواجهة الأساسية لأي محرك تشغيل نماذج داخل نبض.
 */
interface ModelRuntimeInterface {
    val runtimeId: String
    val displayName: String
    val runtimeType: ModelRuntimeType // (LITERT, GGUF, etc.)

    /** تهيئة المحرك وتحميل النموذج من المسار المحدد. */
    suspend fun initialize(modelPath: String): RuntimeStatus

    /** توليد النص بشكل تدفقي (Streaming). */
    suspend fun generate(
        request: RuntimeGenerationRequest,
        onChunk: suspend (RuntimeGenerationChunk) -> Unit
    ): RuntimeGenerationResult

    /** إيقاف عملية التوليد الحالية فوراً. */
    suspend fun stopGeneration()

    /** تحرير الموارد وإغلاق المحرك. */
    suspend fun release()

    /** التحقق مما إذا كان المحرك متاحاً للعمل على الجهاز الحالي. */
    fun isAvailable(): Boolean
}

/** البيانات المطلوبة لعملية التوليد. */
data class RuntimeGenerationRequest(
    val prompt: String,
    val systemPrompt: String? = null,
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val stream: Boolean = true
)

/** وحدة النص المتدفقة من المحرك. */
data class RuntimeGenerationChunk(
    val text: String,
    val tokenIndex: Int? = null,
    val isFinal: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/** النتيجة النهائية لعملية التوليد. */
data class RuntimeGenerationResult(
    val fullText: String,
    val metrics: RuntimeMetrics,
    val error: RuntimeError? = null
)

/** تعريف أخطاء محركات التشغيل. */
sealed class RuntimeError {
    data class ModelFileMissing(val path: String) : RuntimeError()
    data class UnsupportedFormat(val extension: String) : RuntimeError()
    data class NativeLibraryMissing(val libraryName: String) : RuntimeError()
    data class OutOfMemory(val requiredMb: Long?) : RuntimeError()
    data class GenerationFailed(val message: String) : RuntimeError()
    data class Unknown(val message: String) : RuntimeError()
}

/** مؤشرات أداء المحرك. */
data class RuntimeMetrics(
    val timeToFirstTokenMs: Long?,
    val totalGenerationTimeMs: Long? = null,
    val tokensGenerated: Int? = null,
    val tokensPerSecond: Double? = null,
    val peakMemoryMb: Long? = null
)
```

## 3. منطق الإدارة والاختيار (Management Logic)

- **QwenRuntimeAdapter:** هو المغلّف (Wrapper) لمحرك LiteRT الحالي، ويقوم بتحويل استدعاءات `ModelRuntimeInterface` إلى عمليات محرك الاستدلال الموجود حالياً.
- **GgufRuntimeAdapter:** (تجريبي) سيقوم مستقبلاً بربط مكتبات GGUF (مثل llama.cpp) بالواجهة الموحدة.
- **RuntimeRegistry:** سجل مركزي يحتوي على كافة المحركات المسجلة في النظام.
- **RuntimeSelectionPolicy:** سياسة اختيار المحرك:
    - للمستخدم العادي: يتم اختيار `QwenRuntimeAdapter` دائماً.
    - للمطور (Dev Mode): يمكن السماح باختيار `GgufRuntimeAdapter` يدوياً لأغراض الاختبار.

## 4. دورة حياة الطلب (Request Lifecycle)
1. يستلم `ChatViewModel` رسالة المستخدم.
2. يستشير `RuntimeSelectionPolicy` لتحديد المحرك المناسب.
3. يطلب من `RuntimeRegistry` نسخة من المحرك المختار.
4. يرسل `RuntimeGenerationRequest` للمحرك.
5. يستقبل `RuntimeGenerationChunk` ويحدث الواجهة لحظياً.
6. يستقبل `RuntimeGenerationResult` ويخزن مؤشرات الأداء.

## 5. قواعد الأمان التقنية
- **Fallback Rule:** في حال فشل تهيئة أي محرك تجريبي، يعود النظام تلقائياً وبشكل صامت لمحرك LiteRT (Qwen).
- **Resource Lock:** لا يسمح بتشغيل محركين في آن واحد لتجنب استهلاك الذاكرة المفرط (Out of Memory).
- **Isolation:** محرك GGUF لا يملك صلاحية الوصول إلى قاعدة بيانات المحادثات المشفرة إلا من خلال واجهة مجردة.

---
**تاريخ التصميم:** 25 مايو 2026
**المرحلة:** تفصيل تقني نظري
**حالة التنفيذ البرمجي:** تمت إضافة Adapters شكلية (Stubs) آمنة فقط لتأسيس البنية التحتية دون تفعيل أي كود Native أو JNI.
