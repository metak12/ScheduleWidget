package com.dmitry.schedulewidget;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public class PopupActivity extends Activity {

    private LinearLayout rootCard;
    private LinearLayout lessonsContainer;
    private LinearLayout tabsContainer;
    private int selectedIndex = 0;
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

        // Определяем, какой день открыть сразу
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek today = now.getDayOfWeek();
        LocalTime timeNow = now.toLocalTime();

        DayOfWeek targetDay = today;
        List<ScheduleWidget.Lesson> todayList = ScheduleWidget.SCHEDULE.get(today);
        if (todayList == null || todayList.isEmpty() || timeNow.isAfter(todayList.get(todayList.size() - 1).end)) {
            for (int i = 1; i <= 7; i++) {
                DayOfWeek next = today.plus(i);
                if (ScheduleWidget.SCHEDULE.containsKey(next)) {
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

        // Прозрачный фон на весь экран (ловит нажатия мимо карточки для плавного закрытия)
        FrameLayout fullScreenRoot = new FrameLayout(this);
        fullScreenRoot.setBackgroundColor(Color.TRANSPARENT);
        fullScreenRoot.setOnClickListener(v -> closeWithAnimation());

        // Главная карточка с полупрозрачным фоном
        rootCard = new LinearLayout(this);
        rootCard.setOrientation(LinearLayout.VERTICAL);
        rootCard.setBackgroundResource(R.drawable.popup_bg);
        rootCard.setClickable(true); // Чтобы клики внутри карточки не закрывали окно
        int padH = dp(18);
        int padV = dp(16);
        rootCard.setPadding(padH, padV, padH, padV);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                (int) (dm.widthPixels * 0.90f), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        fullScreenRoot.addView(rootCard, cardLp);

        // Заголовок
        TextView header = new TextView(this);
        header.setText("Расписание уроков");
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        header.setTypeface(null, Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        rootCard.addView(header);

        // Панель выбора дня недели (ПН ВТ СР ЧТ ПТ)
        tabsContainer = new LinearLayout(this);
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setWeightSum(5f);
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabsLp.topMargin = dp(12);
        tabsLp.bottomMargin = dp(10);
        rootCard.addView(tabsContainer, tabsLp);

        // Прокручиваемый список уроков на пол-экрана
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        lessonsContainer = new LinearLayout(this);
        lessonsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(lessonsContainer);

        int maxListHeight = (int) (dm.heightPixels * 0.44f);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxListHeight);
        rootCard.addView(scrollView, scrollLp);

        // Подсказка внизу
        TextView hint = new TextView(this);
        hint.setText("Свайпай влево/вправо • Нажми мимо для закрытия");
        hint.setTextColor(Color.parseColor("#80FFFFFF"));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(8);
        rootCard.addView(hint, hintLp);

        setContentView(fullScreenRoot);

        // Настраиваем окно на весь экран с мягким затемнением обоев
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.dimAmount = 0.35f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(wlp);
        }

        // Распознавание горизонтальных свайпов для переключения дней
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
                        // Свайп влево -> следующий день
                        switchDay((selectedIndex + 1) % DAYS.length, 1);
                    } else {
                        // Свайп вправо -> предыдущий день
                        switchDay((selectedIndex - 1 + DAYS.length) % DAYS.length, -1);
                    }
                    return true;
                }
                return false;
            }
        });

        renderTabs();
        renderLessons();

        // Анимация плавного появления окна (пружинящий зум + прозрачность)
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
        List<ScheduleWidget.Lesson> list = ScheduleWidget.SCHEDULE.getOrDefault(selectedDay, Collections.emptyList());

        LocalDateTime now = LocalDateTime.now();
        boolean isToday = (now.getDayOfWeek() == selectedDay);
        LocalTime timeNow = now.toLocalTime();

        for (ScheduleWidget.Lesson lesson : list) {
            boolean isCurrent = isToday && !timeNow.isBefore(lesson.start) && timeNow.isBefore(lesson.end);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pH = dp(14);
            int pV = dp(10);
            row.setPadding(pH, pV, pH, pV);

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(dp(16));
            if (isCurrent) {
                rowBg.setColor(Color.parseColor("#38FFFFFF"));
                rowBg.setStroke(dp(1), Color.parseColor("#90FFFFFF"));
            } else {
                rowBg.setColor(Color.parseColor("#18FFFFFF"));
            }
            row.setBackground(rowBg);

            // Номер урока и предмет
            TextView title = new TextView(this);
            title.setText(lesson.number + ". " + lesson.name);
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            title.setTypeface(null, isCurrent ? Typeface.BOLD : Typeface.NORMAL);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(title, titleLp);

            // Время звонков
            TextView time = new TextView(this);
            time.setText(lesson.timeRange());
            time.setTextColor(isCurrent ? Color.WHITE : Color.parseColor("#CCFFFFFF"));
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            if (isCurrent) {
                time.setTypeface(null, Typeface.BOLD);
            }
            row.addView(time);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(6);
            lessonsContainer.addView(row, rowLp);
        }
    }

    private int dp(int val) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }
                }
