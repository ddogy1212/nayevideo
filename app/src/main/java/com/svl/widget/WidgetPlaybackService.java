package com.svl.widget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * RemoteViews cannot host VideoView. Update the ImageView at ~12fps
 * while MediaPlayer plays the actual MP4 audio. No Activity is launched.
 */
public class WidgetPlaybackService extends Service {
    private static final int FPS = 12;
    private static final int NOTIFICATION_ID = 87;
    private static final String CHANNEL_ID = "svl_video_playback";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int generation = 0;
    private int activeId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private MediaPlayer player;
    private Runnable ticker;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                "svl 위젯 영상", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        int id = intent == null ? AppWidgetManager.INVALID_APPWIDGET_ID :
                intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        File video = WidgetMedia.video(this);
        if (video == null || !video.isFile()) {
            SvlWidgetProvider.render(this, AppWidgetManager.getInstance(this), id);
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        stopCurrent(true);
        activeId = id;
        final int run = ++generation;
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("svl")
                .setContentText("홈 화면 위젯 영상 재생 중")
                .setOngoing(true).build();
        startForeground(NOTIFICATION_ID, notification);
        new Thread(() -> prepareVideo(video, run), "svl-decode").start();
        return START_NOT_STICKY;
    }

    private void prepareVideo(File file, int run) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        List<Bitmap> frames = new ArrayList<>();
        int frameDurationMs = 1000 / FPS;
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            int duration = raw == null ? 2500 : Integer.parseInt(raw);
            int count = Math.min(240, Math.max(1, (duration * FPS + 999) / 1000));
            for (int i = 0; i < count && run == generation; i++) {
                Bitmap frame = retriever.getFrameAtTime(i * 1000000L / FPS,
                        MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame != null) frames.add(WidgetMedia.square(frame, 240));
                else frames.add(frames.isEmpty()
                        ? WidgetMedia.photo(this, 240) : frames.get(frames.size() - 1));
            }
        } catch (Exception e) {
            Log.e("svl", "Cannot decode video", e);
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
        handler.post(() -> {
            if (run != generation) { recycle(frames); return; }
            if (frames.isEmpty()) { finish(run, frames); return; }
            startSynchronizedPlayback(file, frames, run, frameDurationMs);
        });
    }

    private void startSynchronizedPlayback(File file, List<Bitmap> frames, int run, int frameMs) {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> finish(run, frames));
            player.setOnErrorListener((mp, what, extra) -> { finish(run, frames); return true; });
            player.prepare();
            player.start();
            final int[] lastFrame = {-1};
            ticker = new Runnable() {
                @Override public void run() {
                    if (run != generation || player == null || !player.isPlaying()) return;
                    int index = Math.min(frames.size() - 1, player.getCurrentPosition() / frameMs);
                    if (index != lastFrame[0]) {
                        RemoteViews views = new RemoteViews(getPackageName(), R.layout.svl_widget);
                        views.setImageViewBitmap(R.id.widget_image, frames.get(index));
                        AppWidgetManager.getInstance(WidgetPlaybackService.this)
                                .partiallyUpdateAppWidget(activeId, views);
                        lastFrame[0] = index;
                    }
                    handler.postDelayed(this, 1000 / FPS);
                }
            };
            ticker.run();
        } catch (Exception e) {
            Log.e("svl", "Cannot play video", e);
            finish(run, frames);
        }
    }

    private void finish(int run, List<Bitmap> frames) {
        if (run != generation) { recycle(frames); return; }
        stopCurrent(true);
        recycle(frames);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void stopCurrent(boolean restorePhoto) {
        if (ticker != null) { handler.removeCallbacks(ticker); ticker = null; }
        if (player != null) {
            player.setOnCompletionListener(null);
            player.setOnErrorListener(null);
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
        if (restorePhoto && activeId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            SvlWidgetProvider.render(this, AppWidgetManager.getInstance(this), activeId);
        }
        activeId = AppWidgetManager.INVALID_APPWIDGET_ID;
    }
    private static void recycle(List<Bitmap> bitmaps) {
        // Bitmap contents have been sent to RemoteViews parcels; do not recycle here.
        bitmaps.clear();
    }
    @Override public void onDestroy() {
        ++generation;
        stopCurrent(true);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
