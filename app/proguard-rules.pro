# WorkManager строит воркеров своей фабрикой рефлексией через публичный
# конструктор (Context, WorkerParameters): R8 не видит этот вызов и без
# keep-правила может вырезать или переименовать класс.
-keep class ru.taurlom.tnote.data.notifications.DailyDigestWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
