# قواعد الحماية لمشروع دليل اليمن
# عند تفعيل التصغير لاحقاً قد نحتاج قواعد للكيانات (models).
-keep class com.dalilyemen.app.data.model.** { *; }

# كوتلن
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
