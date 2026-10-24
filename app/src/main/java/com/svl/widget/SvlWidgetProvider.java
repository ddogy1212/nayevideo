package com.svl.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

public class SvlWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id);
    }
    @Override public void onAppWidgetOptionsChanged(Context context,
            AppWidgetManager manager, int id, Bundle options) {
        render(context, manager, id);
    }
    public static void render(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.svl_widget);
        views.setImageViewBitmap(R.id.widget_image, WidgetMedia.photo(context, 240));
        Intent play = new Intent(context, WidgetPlaybackService.class);
        play.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        PendingIntent pending = PendingIntent.getForegroundService(context, id, play,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_image, pending);
        manager.updateAppWidget(id, views);
    }
    public static void renderAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, SvlWidgetProvider.class));
        for (int id : ids) render(context, manager, id);
    }
}
