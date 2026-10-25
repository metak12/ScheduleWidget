package com.dmitry.schedulewidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleWidget extends AppWidgetProvider {

    private static final String ACTION_AUTO_UPDATE = "com.dmitry.schedulewidget.ACTION_AUTO_UPDATE";

    private static class Lesson {
        final int number;
        final String name;
        final LocalTime start;
        final LocalTime end;

        Lesson(int number, String name, String startStr, String endStr) {
            this.number = number;
            this.name = name;
            this.start = LocalTime.parse(startStr);
            this.end = LocalTime.parse(endStr);
        }

        String timeRange() {
            return String.format("%02d:%02d–%02d:%02d",
                    start.getHour(), start.getMinute(),
                    end.getHour(), end.getMinute());
        }
    }

    private static final Map<DayOfWeek, List<Lesson>> SCHEDULE = new HashMap<>();

    static {
        // Понедельник
        SCHEDULE.put(DayOfWeek.MONDAY, Arrays.asList(
                new Lesson(1, "Разговоры о важном", "08:00", "08:30"),
                new Lesson(2, "География", "08:35", "09:15"),
                new Lesson(3, "Обществознание", "09:30", "10:10"),
                new Lesson(4, "История", "10:20", "11:00"),
                new Lesson(5, "Химия", "11:15", "11:55"),
                new Lesson(6, "Физика", "12:10", "12:50"),
                new Lesson(7, "Литература", "13:05", "13:45")
        ));

        // Вторник (ко 2-му уроку к 08:55)
        SCHEDULE.put(DayOfWeek.TUESDAY, Arrays.asList(
                new Lesson(2, "Труд (технология)", "08:55", "09:35"),
                new Lesson(3, "Алгебра", "09:50", "10:30"),
                new Lesson(4, "Русский язык", "10:50", "11:30"),
                new Lesson(5, "Химия", "11:45", "12:25"),
                new Lesson(6, "Англ. яз.", "12:45", "13:25"),
                new Lesson(7, "Русский язык", "13:35", "14:15")
        ));

        // Среда
        SCHEDULE.put(DayOfWeek.WEDNESDAY, Arrays.asList(
                new Lesson(1, "Физкультура", "08:00", "08:40"),
                new Lesson(2, "Биология", "08:55", "09:35"),
                new Lesson(3, "Алгебра", "09:50", "10:30"),
                new Lesson(4, "История", "10:50", "11:30"),
                new Lesson(5, "Англ. яз.", "11:45", "12:25"),
                new Lesson(6, "Физика", "12:45", "13:25"),
                new Lesson(7, "Алгебра", "13:35", "14:15")
        ));

        // Четверг
        SCHEDULE.put(DayOfWeek.THURSDAY, Arrays.asList(
                new Lesson(1, "Физкультура", "08:00", "08:40"),
                new Lesson(2, "Литература", "08:55", "09:35"),
                new Lesson(3, "Алгебра", "09:50", "10:30"),
                new Lesson(4, "Физика", "10:50", "11:30"),
                new Lesson(5, "Геометрия", "11:45", "12:25"),
                new Lesson(6, "Англ. яз.", "12:45", "13:25")
        ));

        // Пятница
        SCHEDULE.put(DayOfWeek.FRIDAY, Arrays.asList(
                new Lesson(1, "Геометрия", "08:00", "08:40"),
                new Lesson(2, "Литература", "08:55", "09:35"),
                new Lesson(3, "География", "09:50", "10:30"),
                new Lesson(4, "Биология", "10:50", "11:30"),
                new Lesson(5, "Русский язык", "11:45", "12:25"),
                new Lesson(6, "Вер и стат", "12:45", "13:25"),
                new Lesson(7, "ОБЗР", "13:35", "14:15")
        ));
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        scheduleNextUpdate(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_AUTO_UPDATE.equals(intent.getAction()) ||
                Intent.ACTION_TIME_CHANGED.equals(intent.getAction()) ||
                Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, ScheduleWidget.class));
            onUpdate(context, manager, ids);
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        LocalDateTime now = LocalDateTime.now();
        DayOfWeek today = now.getDayOfWeek();
        LocalTime timeNow = now.toLocalTime();

        List<Lesson> todayLessons = SCHEDULE.getOrDefault(today, Collections.emptyList());

        String statusText = "";
        String subjectText = "";
        String bottomText = "";

        boolean foundToday = false;

        for (int i = 0; i < todayLessons.size(); i++) {
            Lesson lesson = todayLessons.get(i);

            // Если мы до начала этого урока (перемена или утро)
            if (timeNow.isBefore(lesson.start)) {
                long minsLeft = Duration.between(timeNow, lesson.start).toMinutes() + 1;
                if (i == 0) {
                    statusText = "СЕГОДНЯ • " + lesson.number + " УРОК";
                } else {
                    statusText = "ПЕРЕМЕНА • ЧЕРЕЗ " + minsLeft + " МИН";
                }
                subjectText = lesson.name;
                bottomText = lesson.timeRange() + (i == 0 && minsLeft <= 120 ? " (через " + minsLeft + " мин)" : "");
                foundToday = true;
                break;
            }

            // Если урок идет прямо сейчас
            if (!timeNow.isBefore(lesson.start) && timeNow.isBefore(lesson.end)) {
                long minsLeft = Duration.between(timeNow, lesson.end).toMinutes() + 1;
                statusText = "СЕЙЧАС (" + lesson.number + " УРОК) • ЕЩЕ " + minsLeft + " МИН";

                if (i + 1 < todayLessons.size()) {
                    Lesson next = todayLessons.get(i + 1);
                    subjectText = "Далее: " + next.name;
                    bottomText = next.number + " урок • " + next.timeRange();
                } else {
                    subjectText = lesson.name;
                    bottomText = "Последний урок на сегодня (" + lesson.timeRange() + ")";
                }
                foundToday = true;
                break;
            }
        }

        // Если на сегодня уроки закончились или сегодня выходной
        if (!foundToday) {
            for (int offset = 1; offset <= 7; offset++) {
                DayOfWeek nextDay = today.plus(offset);
                List<Lesson> nextLessons = SCHEDULE.get(nextDay);
                if (nextLessons != null && !nextLessons.isEmpty()) {
                    Lesson first = nextLessons.get(0);
                    String dayLabel = offset == 1 ? "ЗАВТРА" : getDayName(nextDay);
                    statusText = dayLabel + " • К " + first.number + "-МУ УРОКУ";
                    subjectText = first.name;
                    bottomText = "Начало в " + first.timeRange() + " (всего уроков: " + nextLessons.size() + ")";
                    break;
                }
            }
        }

        views.setTextViewText(R.id.tv_status, statusText);
        views.setTextViewText(R.id.tv_subject, subjectText);
        views.setTextViewText(R.id.tv_time_and_next, bottomText);

        // Обновление по клику на виджет
        Intent clickIntent = new Intent(context, ScheduleWidget.class);
        clickIntent.setAction(ACTION_AUTO_UPDATE);
        PendingIntent pendingClick = PendingIntent.getBroadcast(
                context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingClick);

        manager.updateAppWidget(widgetId, views);
    }

    private String getDayName(DayOfWeek day) {
        switch (day) {
            case MONDAY: return "В ПОНЕДЕЛЬНИК";
            case TUESDAY: return "ВО ВТОРНИК";
            case WEDNESDAY: return "В СРЕДУ";
            case THURSDAY: return "В ЧЕТВЕРГ";
            case FRIDAY: return "В ПЯТНИЦУ";
            default: return "";
        }
    }

    private void scheduleNextUpdate(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ScheduleWidget.class);
        intent.setAction(ACTION_AUTO_UPDATE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        ZonedDateTime nextMinute = ZonedDateTime.now(ZoneId.systemDefault())
                .plusMinutes(1).withSecond(0).withNano(0);
        long triggerAtMillis = nextMinute.toInstant().toEpochMilli();

        try {
            alarmManager.setExact(AlarmManager.RTC, triggerAtMillis, pendingIntent);
        } catch (SecurityException e) {
            alarmManager.set(AlarmManager.RTC, triggerAtMillis, pendingIntent);
        }
    }
}
