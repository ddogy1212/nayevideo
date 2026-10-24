package com.svl.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_PHOTO = 101;
    private static final int REQUEST_VIDEO = 102;
    private ImageView preview;
    private TextView mediaStatus;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(18, 17, 23));
        getWindow().setNavigationBarColor(Color.rgb(18, 17, 23));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(18, 17, 23));
        LinearLayout root = new LinearLayout(this);
        root.setPadding(dp(24), dp(44), dp(24), dp(24));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);
        root.addView(text("svl", 46, Color.WHITE, true));
        root.addView(text("a little moment, right here ♡", 15, 0xFFFF8CBD, false));
        addSpace(root, 29);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable border = new GradientDrawable();
        border.setColor(0xFF211E29);
        border.setCornerRadius(dp(22));
        preview.setBackground(border);
        preview.setClipToOutline(true);
        root.addView(preview, new LinearLayout.LayoutParams(dp(242), dp(242)));
        addSpace(root, 20);
        mediaStatus = text("", 14, 0xFFD7D2DC, false);
        mediaStatus.setGravity(Gravity.CENTER);
        root.addView(mediaStatus);
        addSpace(root, 25);
        root.addView(button("기본 사진 바꾸기", () -> choose("image/*", REQUEST_PHOTO)));
        addSpace(root, 12);
        root.addView(button("영상 바꾸기", () -> choose("video/*", REQUEST_VIDEO)));
        addSpace(root, 12);
        root.addView(button("홈 화면에 2×2 위젯 추가", this::pinWidget));
        addSpace(root, 24);
        TextView help = text("① 사진과 영상을 설정하세요.\n② 위젯을 홈 화면에 추가하세요.\n③ 위젯을 누르면 영상과 소리가 재생됩니다.\n④ 끝나면 사진으로 돌아갑니다.", 14, 0xFFAAA5B3, false);
        help.setLineSpacing(dp(5), 1f);
        root.addView(help);
        setContentView(scroll);
        updatePreview();
    }
    private void choose(String type, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(type);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "파일 선택"), requestCode);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode != REQUEST_PHOTO && requestCode != REQUEST_VIDEO) return;
        String filename = requestCode == REQUEST_PHOTO ? WidgetMedia.PHOTO : WidgetMedia.VIDEO;
        try (InputStream input = getContentResolver().openInputStream(data.getData());
             FileOutputStream output = new FileOutputStream(WidgetMedia.file(this, filename))) {
            if (input == null) throw new IllegalStateException("파일을 읽지 못했습니다.");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            Toast.makeText(this, "저장했어! ♡", Toast.LENGTH_SHORT).show();
            updatePreview();
            SvlWidgetProvider.renderAll(this);
        } catch (Exception e) {
            Toast.makeText(this, "파일 저장 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void updatePreview() {
        Bitmap bitmap = WidgetMedia.photo(this, 480);
        preview.setImageBitmap(bitmap);
        mediaStatus.setText(WidgetMedia.video(this) == null
                ? "영상이 아직 없어. 아래에서 영상을 선택해 줘."
                : "준비 완료 · 위젯을 눌러 영상 재생 ♥");
    }
    private void pinWidget() {
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        ComponentName widget = new ComponentName(this, SvlWidgetProvider.class);
        if (manager.isRequestPinAppWidgetSupported()) {
            manager.requestPinAppWidget(widget, null, null);
        } else {
            Toast.makeText(this, "홈 화면 빈 곳 길게 누르기 → 위젯 → svl → 추가", Toast.LENGTH_LONG).show();
        }
    }
    private Button button(String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(15);
        b.setTextColor(Color.rgb(30, 22, 31));
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFF91C0);
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        b.setOnClickListener(v -> action.run());
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        return b;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        return t;
    }
    private void addSpace(LinearLayout l, int height) {
        View s = new View(this);
        l.addView(s, new LinearLayout.LayoutParams(1, dp(height)));
    }
    private int dp(int px) { return (int)(px * getResources().getDisplayMetrics().density + .5f); }
}
