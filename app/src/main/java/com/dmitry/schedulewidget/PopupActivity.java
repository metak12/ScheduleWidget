package com.dmitry.schedulewidget;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class PopupActivity extends Activity {

    private LinearLayout rootCard;
    private LinearLayout lessonsContainer;
    private LinearLayout tabsContainer;
    private TextView editBtn;
    private TextView hintText;

    private Map<DayOfWeek, List<ScheduleWidget.Lesson>> schedule;
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

        schedule = ScheduleWidget.loadSchedule(this);

        LocalDateTime now = LocalDateTime.now();
        DayOfWeek today = now.getDayOfWeek();
        LocalTime timeNow = now.toLocalTime();

        DayOfWeek targetDay = today;
        List<ScheduleWidget.Lesson> todayList = schedule.get(today);
        if (todayList == null || todayList.isEmpty() || timeNow.isAfter(todayList.get(todayList.size() - 1).end)) {
            for (int i = 1; i <= 7; i++) {
                DayOfWeek next = today.plus(i);
                List<ScheduleWidget.Lesson> nextList = schedule.get(next);
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

        tabsContainer = new LinearLayout(this);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setWeightSum(5f);
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabsLp.topMargin = dp(12);
        tabsLp.bottomMargin = dp(10);
        rootCard.addView(tabsContainer, tabsLp);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        lessonsContainer = new LinearLayout(this);
        lessonsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(lessonsContainer);

        int maxListHeight = (int) (dm.heightPixels * 0.46f);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxListHeight);
        rootCard.addView(scrollView, scrollLp);

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
        List<ScheduleWidget.Lesson> list = schedule.computeIfAbsent(selectedDay, k -> new ArrayList<>());

        LocalDateTime now = LocalDateTime.now();
        boolean isToday = (now.getDayOfWeek() == selectedDay);
        LocalTime timeNow = now.toLocalTime();

        for (int i = 0; i < list.size(); i++) {
            final int lessonIndex = i;
            ScheduleWidget.Lesson lesson = list.get(i);
            boolean isCurrent = isToday && !timeNow.isBefore(lesson.start) && timeNow.isBefore(lesson.end);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pH = dp(14);
            int pV = dp(10);
            row.setPadding(pH, pV, pH, pV);

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(dp(16));
            if (isEditMode) {
                rowBg.setColor(Color.parseColor("#28FFFFFF"));
                rowBg.setStroke(dp(1), Color.parseColor("#65FFFFFF"));
            } else if (isCurrent) {
                rowBg.setColor(Color.parseColor("#38FFFFFF"));
                rowBg.setStroke(dp(1), Color.parseColor("#90FFFFFF"));
            } else {
                rowBg.setColor(Color.parseColor("#18FFFFFF"));
            }
            row.setBackground(rowBg);

            TextView title = new TextView(this);
            title.setText(lesson.number + ". " + lesson.name);
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            title.setTypeface(null, isCurrent ? Typeface.BOLD : Typeface.NORMAL);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(title, titleLp);

            TextView time = new TextView(this);
            time.setText(lesson.timeRange() + (isEditMode ? "  ✎" : ""));
            time.setTextColor(isCurrent ? Color.WHITE : Color.parseColor("#CCFFFFFF"));
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            if (isCurrent) {
                time.setTypeface(null, Typeface.BOLD);
            }
            row.addView(time);

            if (isEditMode) {
                row.setOnClickListener(v -> showEditLessonDialog(selectedDay, lessonIndex));
            }

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(6);
            lessonsContainer.addView(row, rowLp);
        }

        if (isEditMode) {
            TextView addBtn = new TextView(this);
            addBtn.setText("+ Добавить урок");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            addBtn.setTypeface(null, Typeface.BOLD);
            addBtn.setGravity(Gravity.CENTER);
            addBtn.setPadding(dp(14), dp(10), dp(14), dp(10));

            GradientDrawable addBg = new GradientDrawable();
            addBg.setCornerRadius(dp(16));
            addBg.setColor(Color.parseColor("#354CAF50"));
            addBg.setStroke(dp(1), Color.parseColor("#8081C784"));
            addBtn.setBackground(addBg);
            addBtn.setOnClickListener(v -> showEditLessonDialog(selectedDay, -1));

            LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            addLp.topMargin = dp(4);
            addLp.bottomMargin = dp(6);
            lessonsContainer.addView(addBtn, addLp);

            TextView resetBtn = new TextView(this);
            resetBtn.setText("Сбросить всё к исходному расписанию");
            resetBtn.setTextColor(Color.parseColor("#FFAB91"));
            resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            resetBtn.setGravity(Gravity.CENTER);
            resetBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
            resetBtn.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Сброс расписания")
                    .setMessage("Вернуть изначальное расписание для всех дней?")
                    .setPositiveButton("Сбросить", (d, w) -> {
                        ScheduleWidget.resetSchedule(this);
                        schedule = ScheduleWidget.loadSchedule(this);
                        renderLessons();
                        Toast.makeText(this, "Исходное расписание восстановлено", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Отмена", null)
                    .show());
            lessonsContainer.addView(resetBtn);
        }
    }

    private void showEditLessonDialog(DayOfWeek day, int index) {
        List<ScheduleWidget.Lesson> list = schedule.computeIfAbsent(day, k -> new ArrayList<>());
        boolean isNew = (index < 0 || index >= list.size());

        int defNum = isNew ? (list.isEmpty() ? 1 : list.get(list.size() - 1).number + 1) : list.get(index).number;
        String defName = isNew ? "" : list.get(index).name;
        String defStart = isNew ? "14:25" : list.get(index).startStr();
        String defEnd = isNew ? "15:05" : list.get(index).endStr();

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(4));

        EditText etNumber = createInput(form, "Номер урока (1–9):", String.valueOf(defNum), InputType.TYPE_CLASS_NUMBER);
        EditText etName = createInput(form, "Название предмета:", defName, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText etStart = createInput(form, "Время начала (ЧЧ:ММ):", defStart, InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        EditText etEnd = createInput(form, "Время окончания (ЧЧ:ММ):", defEnd, InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle(isNew ? "Новый урок" : "Изменить урок");
        builder.setView(form);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            try {
                int num = Integer.parseInt(etNumber.getText().toString().trim());
                String name = etName.getText().toString().trim();
                String start = normalizeTime(etStart.getText().toString().trim());
                String end = normalizeTime(etEnd.getText().toString().trim());

                if (name.isEmpty()) {
                    Toast.makeText(this, "Введите название предмета", Toast.LENGTH_SHORT).show();
                    return;
                }

                ScheduleWidget.L
