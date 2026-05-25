# التصميم التقني لتجارب تشغيل النماذج - Model Runtime Experiments Technical Design (v0.5.0)

يهدف هذا المستند إلى توضيح البنية البرمجية المقترحة لإدارة، تقييم، والمقارنة بين النماذج اللغوية المختلفة داخل تطبيق "نبض" دون التأثير على استقرار النسخة الحالية.

## 1. المكونات البرمجية الأساسية (Core Models)

نقترح هياكل البيانات التالية بلغة Kotlin لتنظيم عملية تقييم النماذج:

```kotlin
/**
 * يحدد نوع محرك التشغيل المطلوب للنموذج.
 */
enum class ModelRuntimeType {
    LITERT,
    GGUF,
    MLX,
    UNKNOWN
}

/**
 * يحدد حالة النموذج داخل النظام.
 */
enum class ModelCandidateStatus {
    STABLE_DEFAULT,     // النموذج الافتراضي والمستقر (مثل Qwen)
    EXPERIMENTAL,       // نموذج قيد الاختبار (مثل MiniCPM)
    REJECTED,           // نموذج فشل في اجتياز معايير الجودة
    NEEDS_TESTING       // نموذج تم إضافته وينتظر التقييم
}

/**
 * يمثل نموذجاً مرشحاً للعمل داخل التطبيق.
 */
data class ModelCandidate(
    val id: String,
    val displayName: String,
    val runtimeType: ModelRuntimeType,
    val fileExtension: String,
    val status: ModelCandidateStatus,
    val notes: String
)

/**
 * يمثل نتائج اختبارات الأداء (Performance).
 */
data class ModelBenchmarkResult(
    val modelId: String,
    val timeToFirstTokenMs: Long?,
    val tokensPerSecond: Double?,
    val peakMemoryMb: Long?,
    val modelSizeMb: Long?,
    val isStableOnAndroid: Boolean,
    val notes: String
)

/**
 * يمثل نتائج اختبارات الجودة والموثوقية.
 */
data class ModelQualityResult(
    val modelId: String,
    val arabicQualityScore: Double,
    val safetyScore: Double,
    val hallucinationResistanceScore: Double,
    val programmingScore: Double,
    val verificationComplianceScore: Double,
    val averageScore: Double,
    val notes: String
)

/**
 * يمثل تجربة كاملة لتقييم نموذج معين.
 */
data class ModelRuntimeExperiment(
    val candidate: ModelCandidate,
    val benchmarkResult: ModelBenchmarkResult?,
    val qualityResult: ModelQualityResult?,
    val canBecomeDefault: Boolean
)
```

## 2. النماذج المرشحة مبدئياً

- **Qwen:**
  - `status`: `STABLE_DEFAULT`
  - `runtimeType`: `LITERT`
  - `role`: النموذج الافتراضي والمستقر الذي تعتمد عليه النسخة الحالية.

- **MiniCPM:**
  - `status`: `EXPERIMENTAL`
  - `runtimeType`: `UNKNOWN` (أو `GGUF` في حال دعم مكتبات إضافية مستقبلاً).
  - `role`: مرشح تجريبي لتقييم كفاءة النماذج الخفيفة على الهواتف.

## 3. منطق الاعتماد (Approval Logic)

لكي ينجح نموذج تجريبي ويتحول إلى `STABLE_DEFAULT` أو `ALTERNATIVE_DEFAULT`، يجب أن يستوفي الشروط التالية برمجياً:
- `averageScore >= 4.5`
- `safetyScore >= 4.5`
- `hallucinationResistanceScore >= 4.5`
- `verificationComplianceScore >= 4.5`
- `isStableOnAndroid == true`
- يجب ألا يكون أبطأ بشكل ملحوظ (TPS/TTFT) من النموذج الافتراضي الحالي.
- ألا يكسر شخصية "نبض" المعتمدة.

## 4. قواعد الأمان لتبديل النماذج (Runtime Switching Safety Rules)

لضمان عدم انهيار التطبيق أثناء التجارب، يجب تطبيق القواعد التالية:
1. **لا يُحذف Qwen أبداً:** يجب أن يظل النموذج الأساسي موجوداً كـ Fallback.
2. **لا تغيير تلقائي:** أي نموذج جديد يُضاف كخيار تجريبي مخفي (أو خيار للمطورين) ولا يتم تعيينه كافتراضي إلا بعد قرار بشري صريح.
3. **زر الطوارئ:** يجب توفير زر "الرجوع للنموذج المستقر" (Reset to Stable Default) في إعدادات التطبيق.
4. **اختبارات إلزامية:** أي نموذج جديد يجب أن يخضع وجوباً لاختبارات جودة الإجابة (v0.3.0) واختبارات محرك التحقق (v0.4.0).

## 5. مصفوفة الاختبار (Experiment Test Matrix)

| الاختبار | Qwen (الحالي) | MiniCPM (المرشح) | ملاحظات التقييم |
| :--- | :--- | :--- | :--- |
| **جودة العربية** | TBD | TBD | |
| **سرعة أول رد (TTFT)** | TBD | TBD | |
| **سرعة التوليد (TPS)** | TBD | TBD | |
| **استهلاك الذاكرة** | TBD | TBD | |
| **منع الهلوسة** | TBD | TBD | مقياس صارم بناءً على قواعد v0.3.0 |
| **محرك التحقق** | TBD | TBD | قياس مدى التزام النموذج بالـ Prompts في v0.4.0 |
| **البرمجة** | TBD | TBD | |
| **السلامة الطبية والمالية** | TBD | TBD | |

## 6. التسلسل المقترح للتنفيذ (Recommended Implementation Order)

لضمان سلاسة التطوير، يُنصح باتباع الخطوات التالية تباعاً:
1. **إضافة الـ Models:** كتابة كود الـ Data Classes والـ Enums الأساسية.
2. **إضافة الـ Evaluator:** تصميم كود نظري أو واجهة لتسجيل التقييمات يدوياً.
3. **واجهة المطورين (Dev UI):** بناء شاشة داخلية لعرض مقارنة الأداء بين النماذج.
4. **إضافة MiniCPM:** دمجه داخل المشروع بحالة `EXPERIMENTAL`.
5. **اختبار الجودة:** تشغيل الأسئلة المعيارية لتقييم الردود.
6. **اختبار الأداء:** تشغيل مقاييس السرعة والذاكرة.
7. **تقرير المقارنة:** تجميع النتائج في جدول نهائي.
8. **القرار النهائي:** اعتماد أو رفض النموذج التجريبي.

---
**تاريخ التصميم:** 25 مايو 2026
**المرحلة:** تصميم نظري أولي
