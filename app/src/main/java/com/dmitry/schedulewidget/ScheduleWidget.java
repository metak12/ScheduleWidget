package com.dmitry.schedulewidget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleWidget extends AppWidgetProvider {

    public static final String ACTION_AUTO_UPDATE = "com.dmitry.schedulewidget.ACTION_AUTO_UPDATE";
    private static final String PREFS_NAME = "SchedulePrefs";
    private static final String KEY_CUSTOM_SCHEDULE = "custom_schedule_v1";
    private static final String KEY_VACATION_START = "vacation_start";
    private static final String KEY_VACATION_END = "vacation_end";

    private static final String[] MONTHS_GEN = {
            "", "ЯНВ", "ФЕВ", "МАР", "АПР", "МАЯ", "ИЮН",
            "ИЮЛ", "АВГ", "СЕН", "ОКТ", "НОЯ", "ДЕК"
    };

    private static final String[] DAY_SHORT = {
            "", "ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"
    };

    public static class Lesson {
        public int number;
        public String name;
        public LocalTime start;
        public LocalTime end;

        public Lesson(int number, String name, String startStr, String endStr) {
            this.number = number;
            this.name = name;
            this.start = LocalTime.parse(startStr);
            this.end = LocalTime.parse(endStr);
        }

        public String startStr() {
            return String.format("%02d:%02d", start.getHour(), start.getMinute());
        }

        public String endStr() {
            return String.format("%02d:%02d", end.getHour(), end.getMinute());
        }

        public String timeRange() {
            return startStr() + "–" + endStr();
        }
    }

    public static String formatDateShort(LocalDate date) {
        return DAY_SHORT[date.getDayOfWeek().getValue()] + ", " +
                date.getDayOfMonth() + " " + MONTHS_GEN[date.getMonthValue()];
    }

    public static void saveVacation(Context context, LocalDate start, LocalDate end) {
        SharedPreferences.Editor ed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        if (start == null || end == null) {
            ed.remove(KEY_VACATION_START).remove(KEY_VACATION_END);
        } else {
            ed.putString(KEY_VACATION_START, start.toString());
            ed.putString(KEY_VACATION_END, end.toString());
        }
        ed.apply();
        triggerWidgetUpdate(context);
    }

    public static LocalDate[] loadVacation(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String s = prefs.getString(KEY_VACATION_START, null);
        String e = prefs.getString(KEY_VACATION_END, null);
        if (s == null || e == null) return null;
        try {
            return new LocalDate[]{LocalDate.parse(s), LocalDate.parse(e)};
        } catch (Exception ex) {
            return null;
        }
    }

    // Изначально пустое расписание для релизной версии
    public static Map<DayOfWeek, List<Lesson>> getDefaultSchedule() {
        Map<DayOfWeek, List<Lesson>> map = new HashMap<>();
        map.put(DayOfWeek.MONDAY, new ArrayList<>());
        map.put(DayOfWeek.TUESDAY, new ArrayList<>());
        map.put(DayOfWeek.WEDNESDAY, new ArrayList<>());
        map.put(DayOfWeek.THURSDAY, new ArrayList<>());
        map.put(DayOfWeek.FRIDAY, new ArrayList<>());
        return map;
    }

    public static Map<DayOfWeek, List<Lesson>> loadSchedule(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString(KEY_CUSTOM_SCHEDULE, null);
        if (jsonStr == null) {
            return getDefaultSchedule();
        }
        try {
            Map<DayOfWeek, List<Lesson>> map = new HashMap<>();
            JSONObject root = new JSONObject(jsonStr);
            for (DayOfWeek day : DayOfWeek.values()) {
                if (root.has(day.name())) {
                    JSONArray arr = root.getJSONArray(day.name());
                    List<Lesson> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        list.add(new Lesson(
                                obj.getInt("number"),
                                obj.getString("name"),
                                obj.getString("start"),
                                obj.getString("end")
                        ));
                    }
                    map.put(day, list);
                }
            }
            return map;
        } catch (Exception e) {
            return getDefaultSchedule();
        }
    }

    public static void saveSchedule(Context context, Map<DayOfWeek, List<Lesson>> schedule) {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<DayOfWeek, List<Lesson>> entry : schedule.entrySet()) {
                JSONArray arr = new JSONArray();
                for (Lesson l : entry.getValue()) {
                    JSONObject obj = new JSONObject();
                    obj.put("number", l.number);
                    obj.put("name", l.name);
                    obj.put("start", l.startStr());
                    obj.put("end", l.endStr());
                    arr.put(obj);
                }
                root.put(entry.getKey().name(), arr);
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CUSTOM_SCHEDULE, root.toString())
                    .apply();
            triggerWidgetUpdate(context);
        } catch (Exception ignored) {
        }
    }

    public static void resetSchedule(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_CUSTOM_SCHEDULE)
                .apply();
        triggerWidgetUpdate(context);
    }

    public static void triggerWidgetUpdate(Context context) {
        Intent intent = new Intent(context, ScheduleWidget.class);
        intent.setAction(ACTION_AUTO_UPDATE);
        context.sendBroadcast(intent);
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
        String action = intent.getAction();
        if (ACTION_AUTO_UPDATE.equals(action) ||
                Intent.ACTION_TIME_CHANGED.equals(action) ||
                Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, ScheduleWidget.class));
            onUpdate(context, manager, ids);
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        Map<DayOfWeek, List<Lesson>> schedule = loadSchedule(context);

        LocalDateTime now = LocalDateTime.now();
        LocalDate dateNow = now.toLocalDate();
        DayOfWeek today = now.getDayOfWeek();
        LocalTime timeNow = now.toLocalTime();

        String dateBadge = formatDateShort(dateNow);

        LocalDate[] vac = loadVacation(context);
        if (vac != null && !dateNow.isBefore(vac[0]) && !dateNow.isAfter(vac[1])) {
            long daysLeft = Duration.between(dateNow.atStartOfDay(), vac[1].plusDays(1).atStartOfDay()).toDays();
            views.setTextViewText(R.id.tv_status, dateBadge + " • КАНИКУЛЫ 🏖️");
            views.setTextViewText(R.id.tv_subject, "Отдыхаем!");
            views.setTextViewText(R.id.tv_time_and_next,
                    "До " + vac[1].getDayOfMonth() + " " + MONTHS_GEN[vac[1].getMonthValue()] + " (осталось дн: " + daysLeft + ")");
            bindClick(context, manager, widgetId, views);
            return;
        }

        // Проверяем, заполнено ли вообще расписание хотя бы в один день
        boolean isCompletelyEmpty = true;
        for (List<Lesson> dayList : schedule.values()) {
            if (dayList != null && !dayList.isEmpty()) {
                isCompletelyEmpty = false;
                break;
            }
        }

        if (isCompletelyEmpty) {
            views.setTextViewText(R.id.tv_status, dateBadge + " • РАСПИСАНИЕ");
            views.setTextViewText(R.id.tv_subject, "Добавь уроки");
            views.setTextViewText(R.id.tv_time_and_next, "Нажми сюда для настройки ✎");
            bindClick(context, manager, widgetId, views);
            return;
        }

        List<Lesson> todayLessons = schedule.getOrDefault(today, Collections.emptyList());

        String statusText = dateBadge + " • ВЫХОДНОЙ";
        String subjectText = "Уроков нет";
        String bottomText = "Отдыхаем";

        boolean foundToday = false;
        LocalTime schoolStartLimit = LocalTime.of(7, 0);

        boolean isSchoolActiveHours = false;
        if (!todayLessons.isEmpty()) {
            LocalTime lastEnd = todayLessons.get(todayLessons.size() - 1).end;
            isSchoolActiveHours = !timeNow.isBefore(schoolStartLimit) && timeNow.isBefore(lastEnd);
        }

        if (isSchoolActiveHours) {
            for (int i = 0; i < todayLessons.size(); i++) {
                Lesson lesson = todayLessons.get(i);

                if (timeNow.isBefore(lesson.start)) {
                    long minsLeft = Duration.between(timeNow, lesson.start).toMinutes() + 1;
                    if (i == 0) {
                        statusText = dateBadge + " • ДО НАЧАЛА " + minsLeft + " МИН";
                    } else {
                        statusText = "ПЕРЕМЕНА • ДО НАЧАЛА " + minsLeft + " МИН";
                    }
                    subjectText = lesson.name;
                    bottomText = lesson.number + " урок • " + lesson.timeRange();
                    foundToday = true;
                    break;
                }

                if (!timeNow.isBefore(lesson.start) && timeNow.isBefore(lesson.end)) {
                    long minsLeft = Duration.between(timeNow, lesson.end).toMinutes() + 1;
                    statusText = "ДО КОНЦА УРОКА • " + minsLeft + " МИН";
                    subjectText = lesson.name;

                    if (i + 1 < todayLessons.size()) {
                        Lesson next = todayLessons.get(i + 1);
                        bottomText = "Далее: " + next.name + " (" + next.timeRange() + ")";
                    } else {
                        bottomText = "Последний урок (" + lesson.timeRange() + ")";
                    }
                    foundToday = true;
                    break;
                }
            }
        } else {
            if (!todayLessons.isEmpty() && timeNow.isBefore(todayLessons.get(todayLessons.size() - 1).end)) {
                for (int i = 0; i < todayLessons.size(); i++) {
                    Lesson lesson = todayLessons.get(i);
                    if (timeNow.isBefore(lesson.start)) {
                        statusText = dateBadge + " • " + lesson.number + " УРОК";
                        subjectText = lesson.name;
                        bottomText = lesson.number + " урок • " + lesson.timeRange();
                        foundToday = true;
                        break;
                    }
                }
            }
        }

        if (!foundToday) {
            for (int offset = 1; offset <= 14; offset++) {
                LocalDate nextDate = dateNow.plusDays(offset);
                if (vac != null && !nextDate.isBefore(vac[0]) && !nextDate.isAfter(vac[1])) {
                    continue;
                }
                DayOfWeek nextDay = nextDate.getDayOfWeek();
                List<Lesson> nextLessons = schedule.get(nextDay);
                if (nextLessons != null && !nextLessons.isEmpty()) {
                    Lesson first = nextLessons.get(0);
                    String dayLabel = offset == 1 ? "ЗАВТРА (" + nextDate.getDayOfMonth() + " " + MONTHS_GEN[nextDate.getMonthValue()] + ")"
                            : formatDateShort(nextDate);
                    statusText = dayLabel + " • К " + first.number + "-МУ УРОКУ";
                    subjectText = first.name;
                    bottomText = "Начало в " + first.timeRange();
                    break;
                }
            }
        }

        views.setTextViewText(R.id.tv_status, statusText);
        views.setTextViewText(R.id.tv_subject, subjectText);
        views.setTextViewText(R.id.tv_time_and_next, bottomText);

        bindClick(context, manager, widgetId, views);
    }

    private void bindClick(Context context, AppWidgetManager manager, int widgetId, RemoteViews views) {
        Intent popupIntent = new Intent(context, PopupActivity.class);
        popupIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingClick = PendingIntent.getActivity(
                context, 0, popupIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingClick);
        manager.updateAppWidget(widgetId, views);
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
