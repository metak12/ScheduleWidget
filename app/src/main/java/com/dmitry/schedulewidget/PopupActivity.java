package com.dmitry.schedulewidget;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public class PopupActivity extends Activity {

    private LinearLayout lessonsContainer;
    private LinearLayout tabsContainer;
    private DayOfWeek selectedDay;

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

        // Определяем, какой день открыть сразу (если уроки сегодня кончились или выходные — открываем следующий учебный день)
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek today = now.getDayOfWeek();
        LocalTime timeNow = now.toLocalTime();

        selectedDay = today;
        List<ScheduleWidget.Lesson> todayList = ScheduleWidget.SCHEDULE.get(today);
        if (todayList == null || todayList.isEmpty() || timeNow.isAfter(todayList.get(todayList.size() - 1).end)) {
            for (int i = 1; i <= 7; i++) {
                DayOfWeek next = today.plus(i);
                if (ScheduleWidget.SCHEDULE.containsKey(next)) {
                    selectedDay = next;
                    break;
                }
            }
        }

        // Главная карточка с полупрозрачным фоном
        LinearLayout rootCard = new LinearLayout(this);
        rootCard.setOrientation(LinearLayout.VERTICAL);
        rootCard.setBackgroundResource(R.drawable.popup_bg);
        int padH = dp(18);
        int padV = dp(16);
        rootCard.setPadding(padH, padV, padH, padV);

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

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxListHeight = (int) (dm.heightPixels * 0.44f);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxListHeight);
        rootCard.addView(scrollView, scrollLp);

        // Подсказка внизу
        TextView hint = new TextView(this);
        hint.setText("Нажми мимо окна, чтобы закрыть");
        hint.setTextColor(Color.parseColor("#80FFFFFF"));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(8);
        rootCard.addView(hint, hintLp);

        setContentView(rootCard);

        // Настраиваем прозрачность окна и закрытие по клику мимо него
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout((int) (dm.widthPixels * 0.90f), ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.dimAmount = 0.35f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(wlp);
        }
        setFinishOnTouchOutside(true);

        renderTabs();
        renderLessons();
    }

    private void renderTabs() {
        tabsContainer.removeAllViews();
        for (int i = 0; i < DAYS.length; i++) {
            final DayOfWeek day = DAYS[i];
            boolean isSelected = (day == selectedDay);

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
                selectedDay = day;
                renderTabs();
                renderLessons();
            });

            tabsContainer.addView(tab, lp);
        }
    }

    private void renderLessons() {
        lessonsContainer.removeAllViews();
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
