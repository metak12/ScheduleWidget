package com.dmitry.schedulewidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class PopupActivity extends Activity {

    private static final String PREFS_NAME = "SchedulePrefs";
    private static final String KEY_CUSTOM_SCHEDULE = "custom_schedule_v2";

    private LinearLayout rootCard;
    private LinearLayout lessonsContainer;
    private LinearLayout tabsContainer;
    private TextView editBtn;
    private TextView hintText;

    private int selectedIndex = 0;
    private boolean isEditMode = false;
    private boolean isClosing = false;

    private GestureDetector gestureDetector;

    private final DayOfWeek[] DAYS = {
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
    };
    private final String[] DAY_SHORT = {"ПН", "ВТ", "СР", "ЧТ", "ПТ"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        overridePendingTransition(0, 0);

        // Загружаем сохраненное расписание в общую память при открытии
        loadCustomScheduleIntoMemory();

        LocalDateTime now = LocalDateTime.now();
        DayOfWeek today = now.getDayOfWeek();
        LocalTime timeNow = now.toLocalTime();

        DayOfWeek targetDay = today;
        List<ScheduleWidget.Lesson> todayList = ScheduleWidget.SCHEDULE.get(today);
        if (todayList == null || todayList.isEmpty() || timeNow.isAfter(todayList.get(todayList.size() - 1).end)) {
            for (int i = 1; i <= 7; i++) {
                DayOfWeek next = today.plus(i);
                List<ScheduleWidget.Lesson> nextList = ScheduleWidget.SCHEDULE.get(next);
                if (nextList != null && !nextList.isEmpty()) {
                    targetDay = next;
                    break;
                }
            }
        }

        for (int i = 0; i < DAYS.length; i++) {
            if (DAYS[i] == targetDay) {
                selectedIndex = i;
                break;
            }
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();

        FrameLayout fullScreenRoot = new FrameLayout(this);
        fullScreenRoot.setBackgroundColor(Color.TRANSPARENT);
        fullScreenRoot.setOnClickListener(v -> closeWithAnimation());

        rootCard = new LinearLayout(this);
        rootCard.setOrientation(LinearLayout.VERTICAL);
        rootCard.setBackgroundResource(R.drawable.popup_bg);
        rootCard.setClickable(true);
        int padH = dp(18);
        int padV = dp(16);
        rootCard.setPadding(padH, padV, padH, padV);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                (int) (dm.widthPixels * 0.90f), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        fullScreenRoot.addView(rootCard, cardLp);

        // Шапка с заголовком и кнопкой карандаша
        FrameLayout headerRow = new FrameLayout(this);

        TextView header = new TextView(this);
        header.setText("Расписание уроков");
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        header.setTypeface(null, Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        headerRow.addView(header, titleLp);

        editBtn = new TextView(this);
        editBtn.setText("✎");
        editBtn.setTextColor(Color.WHITE);
        editBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        editBtn.setGravity(Gravity.CENTER);
        updateEditButtonStyle();
        editBtn.setOnClickListener(v -> {
            isEditMode = !isEditMode;
            updateEditButtonStyle();
            renderLessons();
            if (isEditMode) {
                Toast.makeText(this, "Нажми на любой урок, чтобы изменить его", Toast.LENGTH_SHORT).show();
            }
        });

        FrameLayout.LayoutParams editLp = new FrameLayout.LayoutParams(dp(34), dp(34));
        editLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        headerRow.addView(editBtn, editLp);

        rootCard.addView(headerRow);

        // Вкладки дней недели
        tabsContainer = new LinearLayout(this);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setWeightSum(5f);
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabsLp.topMargin = dp(12);
        tabsLp.bottomMargin = dp(10);
        rootCard.addView(tabsContainer, tabsLp);

        // Прокручиваемый список уроков
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        lessonsContainer = new LinearLayout(this);
        lessonsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(lessonsContainer);

        int maxListHeight = (int) (dm.heightPixels * 0.46f);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxListHeight);
        rootCard.addView(scrollView, scrollLp);

        // Подсказка внизу
        hintText = new TextView(this);
        hintText.setText("Свайпай влево/вправо • ✎ изменить расписание");
        hintText.setTextColor(Color.parseColor("#80FFFFFF"));
        hintText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hintText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(8);
        rootCard.addView(hintText, hintLp);

        setContentView(fullScreenRoot);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.dimAmount = 0.35f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(wlp);
        }

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 90;
            private static final int SWIPE_VELOCITY_THRESHOLD = 90;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY) &&
                        Math.abs(diffX) > SWIPE_THRESHOLD &&
                        Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX < 0) {
                        switchDay((selectedIndex + 1) % DAYS.length, 1);
                    } else {
                        switchDay((selectedIndex - 1 + DAYS.length) % DAYS.length, -1);
                    }
                    return true;
                }
                return false;
            }
        });

        renderTabs();
        renderLessons();

        rootCard.setAlpha(0f);
        rootCard.setScaleX(0.82f);
        rootCard.setScaleY(0.82f);
        rootCard.setTranslationY(dp(25));
        rootCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(280)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .start();
    }

    private void loadCustomScheduleIntoMemory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString(KEY_CUSTOM_SCHEDULE, null);
        if (jsonStr == null) return;
        try {
            JSONObject root = new JSONObject(jsonStr);
            for (DayOfWeek day : DAYS) {
                if (root.has(day.name())) {
                    JSONArray arr = root.getJSONArray(day.name());
                    List<ScheduleWidget.Lesson> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        list.add(new ScheduleWidget.Lesson(
                                obj.getInt("number"),
                                obj.getString("name"),
                                obj.getString("start"),
                                obj.getString("end")
                        ));
                    }
                    ScheduleWidget.SCHEDULE.put(day, list);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveCustomScheduleFromMemory() {
        try {
            JSONObject root = new JSONObject();
            for (DayOfWeek day : DAYS) {
                List<ScheduleWidget.Lesson> list = ScheduleWidget.SCHEDULE.get(day);
                if (list == null) continue;
                JSONArray arr = new JSONArray();
                for (ScheduleWidget.Lesson l : list) {
                    JSONObject obj = new JSONObject();
                    obj.put("number", l.number);
                    obj.put("name", l.name);
                    obj.put("start", formatTime(l.start));
                    obj.put("end", formatTime(l.end));
                    arr.put(obj);
                }
                root.put(day.name(), arr);
            }
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CUSTOM_SCHEDULE, root.toString())
                    .apply();
            notifyWidgetUpdate();
        } catch (Exception ignored) {
        }
    }

    private void resetToDefaultSchedule() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_CUSTOM_SCHEDULE)
                .apply();

        ScheduleWidget.SCHEDULE.put(DayOfWeek.MONDAY, new ArrayList<>(Arrays.asList(
                new ScheduleWidget.Lesson(1, "Разговоры о важном", "08:00", "08:30"),
                new ScheduleWidget.Lesson(2, "География", "08:35", "09:15"),
                new ScheduleWidget.Lesson(3, "Обществознание", "09:30", "10:10"),
                new ScheduleWidget.Lesson(4, "История", "10:20", "11:00"),
                new ScheduleWidget.Lesson(5, "Химия", "11:15", "11:55"),
                new ScheduleWidget.Lesson(6, "Физика", "12:10", "12:50"),
                new ScheduleWidget.Lesson(7, "Литература", "13:05", "13:45")
        )));
        ScheduleWidget.SCHEDULE.put(DayOfWeek.TUESDAY, new ArrayList<>(Arrays.asList(
                new ScheduleWidget.Lesson(2, "Труд (технология)", "08:55", "09:35"),
                new ScheduleWidget.Lesson(3, "Алгебра", "09:50", "10:30"),
                new ScheduleWidget.Lesson(4, "Русский язык", "10:50", "11:30"),
                new ScheduleWidget.Lesson(5, "Химия", "11:45", "12:25"),
                new ScheduleWidget.Lesson(6, "Англ. яз.", "12:45", "13:25"),
                new ScheduleWidget.Lesson(7, "Русский язык", "13:35", "14:15")
        )));
        ScheduleWidget.SCHEDULE.put(DayOfWeek.WEDNESDAY, new ArrayList<>(Arrays.asList(
                new ScheduleWidget.Lesson(1, "Физкультура", "08:00", "08:40"),
                new ScheduleWidget.Lesson(2, "Биология", "08:55", "09:35"),
                new ScheduleWidget.Lesson(3, "Алгебра", "09:50", "10:30"),
                new ScheduleWidget.Lesson(4, "История", "10:50", "11:30"),
                new ScheduleWidget.Lesson(5, "Англ. яз.", "11:45", "12:25"),
                new ScheduleWidget.Lesson(6, "Физика", "12:45", "13:25"),
                new ScheduleWidget.Lesson(7, "Алгебра", "13:35", "14:15")
        )));
        ScheduleWidget.SCHEDULE.put(DayOfWeek.THURSDAY, new ArrayList<>(Arrays.asList(
                new ScheduleWidget.Lesson(1, "Физкультура", "08:00", "08:40"),
                new ScheduleWidget.Lesson(2, "Литература", "08:55", "09:35"),
                new ScheduleWidget.Lesson(3, "Алгебра", "09:50", "10:30"),
                new ScheduleWidget.Lesson(4, "Физика", "10:50", "11:30"),
                new ScheduleWidget.Lesson(5, "Геометрия", "11:45", "12:25"),
                new ScheduleWidget.Lesson(6, "Англ. яз.", "12:45", "13:25")
        )));
        ScheduleWidget.SCHEDULE.put(DayOfWeek.FRIDAY, new ArrayList<>(Arrays.asList(
                new ScheduleWidget.Lesson(1, "Геометрия", "08:00", "08:40"),
                new ScheduleWidget.Lesson(2, "Литература", "08:55", "09:35"),
                new ScheduleWidget.Lesson(3, "География", "09:50", "10:30"),
                new ScheduleWidget.Lesson(4, "Биология", "10:50", "11:30"),
                new ScheduleWidget.Lesson(5, "Русский язык", "11:45", "12:25"),
                new ScheduleWidget.Lesson(6, "Вер и стат", "12:45", "13:25"),
                new ScheduleWidget.Lesson(7, "ОБЗР", "13:35", "14:15")
        )));
        notifyWidgetUpdate();
    }

    private void notifyWidgetUpdate() {
        Intent intent = new Intent(this, ScheduleWidget.class);
        intent.setAction(ScheduleWidget.ACTION_AUTO_UPDATE);
        sendBroadcast(intent);
    }

    private String formatTime(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private void updateEditButtonStyle() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(17));
        if (isEditMode) {
            editBtn.setText("✓");
            bg.setColor(Color.parseColor("#554CAF50"));
            bg.setStroke(dp(1), Color.parseColor("#B081C784"));
            if (hintText != null) {
                hintText.setText("Режим правки: нажми на урок для изменения");
            }
        } else {
            editBtn.setText("✎");
            bg.setColor(Color.parseColor("#25FFFFFF"));
            bg.setStroke(dp(1), Color.parseColor("#50FFFFFF"));
            if (hintText != null) {
                hintText.setText("Свайпай влево/вправо • ✎ изменить расписание");
            }
        }
        editBtn.setBackground(bg);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestureDetector != null && gestureDetector.onTouchEvent(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void switchDay(int newIndex, int direction) {
        if (newIndex == selectedIndex) return;
        selectedIndex = newIndex;
        renderTabs();

        float offset = dp(45) * direction;
        lessonsContainer.animate()
                .alpha(0f)
                .translationX(-offset)
                .setDuration(110)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    renderLessons();
                    lessonsContainer.setTranslationX(offset);
                    lessonsContainer.animate()
                            .alpha(1f)
                            .translationX(0f)
                            .setDuration(170)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private void closeWithAnimation() {
        if (isClosing) return;
        isClosing = true;
        rootCard.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .translationY(dp(20))
                .setDuration(180)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                })
                .start();
    }

    @Override
    public void onBackPressed() {
        if (isEditMode) {
            isEditMode = false;
            updateEditButtonStyle();
            renderLessons();
            return;
        }
        closeWithAnimation();
    }

    private void renderTabs() {
        tabsContainer.removeAllViews();
        for (int i = 0; i < DAYS.length; i++) {
            final int index = i;
            boolean isSelected = (i == selectedIndex);

            TextView tab = new TextView(this);
            tab.setText(DAY_SHORT[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tab.setTypeface(null, Typeface.BOLD);
            tab.setPadding(0, dp(8), 0, dp(8));

            GradientDrawable tabBg = new GradientDrawable();
            tabBg.setCornerRadius(dp(14));
            if (isSelected) {
                tabBg.setColor(Color.parseColor("#45FFFFFF"));
                tabBg.setStroke(dp(1), Color.parseColor("#90FFFFFF"));
                tab.setTextColor(Color.WHITE);
            } else {
                tabBg.setColor(Color.parseColor("#15FFFFFF"));
                tab.setTextColor(Color.parseColor("#B0FFFFFF"));
            }
            tab.setBackground(tabBg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);

            tab.setOnClickListener(v -> {
                if (index != selectedIndex) {
                    int dir = index > selectedIndex ? 1 : -1;
                    switchDay(index, dir);
                }
            });

            tabsContainer.addView(tab, lp);
        }
    }

    private void renderLessons() {
        lessonsContainer.removeAllViews();
        DayOfWeek selectedDay = DAYS[selectedIndex];
        List<ScheduleWidget.Lesson> currentList = ScheduleWidget.SCHEDULE.get(selectedDay);
        final List<ScheduleWidget.Lesson> list = (currentList != null) ? new ArrayList<>(currentList) : new ArrayList<>();
        ScheduleWidget.SCHEDULE.put(selectedDay, list);

        LocalDateTime now = LocalDateTime.now();
        boolean isToday = (now.getDayOfWeek() == selectedDay);
        LocalTime timeNow = now.toLocalTime();

        for (int i = 0; i < list.size(); i++) {
            final int lessonIndex = i;
            ScheduleWidget.Lesson lesson = list.get(i);
            boolean isCurrent = isToday && !timeNow.isBefore(lesson.start) && timeNow.isBefore(lesson.end);

            LinearL
