package com.dmitry.schedulewidget;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PopupActivity extends Activity {

    private static final int PICK_IMAGE_REQ = 1001;

    private LinearLayout rootCard;
    private LinearLayout lessonsContainer;
    private LinearLayout tabsContainer;
    private TextView editBtn;
    private TextView vacBtn;
    private TextView subHeaderDate;
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
                (int) (dm.widthPixels * 0.92f), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        fullScreenRoot.addView(rootCard, cardLp);

        FrameLayout headerRow = new FrameLayout(this);

        vacBtn = new TextView(this);
        vacBtn.setText("🏖️");
        vacBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        vacBtn.setGravity(Gravity.CENTER);
        updateVacButtonStyle();
        vacBtn.setOnClickListener(v -> showVacationDialog());
        FrameLayout.LayoutParams vacLp = new FrameLayout.LayoutParams(dp(36), dp(36));
        vacLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        headerRow.addView(vacBtn, vacLp);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);

        TextView header = new TextView(this);
        header.setText("Расписание");
        header.setTextColor(Color.WHITE);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        header.setTypeface(null, Typeface.BOLD);
        header.setGravity(Gravity.CENTER);
        titleBox.addView(header);

        subHeaderDate = new TextView(this);
        subHeaderDate.setTextColor(Color.parseColor("#B0FFFFFF"));
        subHeaderDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subHeaderDate.setGravity(Gravity.CENTER);
        titleBox.addView(subHeaderDate);

        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        headerRow.addView(titleBox, titleLp);

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
        });

        FrameLayout.LayoutParams editLp = new FrameLayout.LayoutParams(dp(36), dp(36));
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

        int maxListHeight = (int) (dm.heightPixels * 0.48f);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, maxListHeight);
        rootCard.addView(scrollView, scrollLp);

        hintText = new TextView(this);
        hintText.setText("Свайпай дни • 🏖️ каникулы • ✎ изменить");
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
            wlp.dimAmount = 0.40f;
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
    private LocalDate getDateForSelectedDay() {
        LocalDate now = LocalDate.now();
        int diff = DAYS[selectedIndex].getValue() - now.getDayOfWeek().getValue();
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY) {
            diff += 7;
        }
        return now.plusDays(diff);
    }

    private void updateVacButtonStyle() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        LocalDate[] vac = ScheduleWidget.loadVacation(this);
        if (vac != null) {
            bg.setColor(Color.parseColor("#55FF9800"));
            bg.setStroke(dp(1), Color.parseColor("#B0FFB74D"));
        } else {
            bg.setColor(Color.parseColor("#25FFFFFF"));
            bg.setStroke(dp(1), Color.parseColor("#50FFFFFF"));
        }
        vacBtn.setBackground(bg);
    }

    private void updateEditButtonStyle() {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        if (isEditMode) {
            editBtn.setText("✓");
            bg.setColor(Color.parseColor("#554CAF50"));
            bg.setStroke(dp(1), Color.parseColor("#B081C784"));
            if (hintText != null) {
                hintText.setText("Нажми на урок или загрузи скриншот дневника");
            }
        } else {
            editBtn.setText("✎");
            bg.setColor(Color.parseColor("#25FFFFFF"));
            bg.setStroke(dp(1), Color.parseColor("#50FFFFFF"));
            if (hintText != null) {
                hintText.setText("Свайпай дни • 🏖️ каникулы • ✎ изменить");
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
        LocalDate selDate = getDateForSelectedDay();
        LocalDate[] vac = ScheduleWidget.loadVacation(this);
        String vacLabel = "";
        if (vac != null && !selDate.isBefore(vac[0]) && !selDate.isAfter(vac[1])) {
            vacLabel = " • КАНИКУЛЫ 🏖️";
        }
        subHeaderDate.setText(ScheduleWidget.formatDateShort(selDate) + vacLabel);

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

        if (isEditMode) {
            TextView scanBtn = createStyledActionButton(
                    "📷 Загрузить день по скриншоту",
                    "#402196F3", "#9064B5F6");
            scanBtn.setOnClickListener(v -> pickScreenshotFromGallery());
            LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            scanLp.bottomMargin = dp(8);
            lessonsContainer.addView(scanBtn, scanLp);
        }

        if (list.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(isEditMode
                    ? "Уроков пока нет.\nЗагрузи скриншот или нажми кнопку ниже 👇"
                    : "В этот день уроков пока нет.\nНажми на карандаш ✎ вверху справа, чтобы добавить расписание!");
            emptyView.setTextColor(Color.parseColor("#B0FFFFFF"));
            emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(dp(16), dp(28), dp(16), dp(28));
            lessonsContainer.addView(emptyView);
        }

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

            TextView numBadge = new TextView(this);
            numBadge.setText(String.valueOf(lesson.number));
            numBadge.setTextColor(Color.WHITE);
            numBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            numBadge.setTypeface(null, Typeface.BOLD);
            numBadge.setGravity(Gravity.CENTER);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(dp(12));
            badgeBg.setColor(Color.parseColor(isCurrent ? "#6081C784" : "#25FFFFFF"));
            numBadge.setBackground(badgeBg);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(24), dp(24));
            badgeLp.rightMargin = dp(10);
            row.addView(numBadge, badgeLp);

            TextView title = new TextView(this);
            title.setText(lesson.name);
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
                row.setOnClickListener(v -> showStyledLessonDialog(selectedDay, lessonIndex));
            }

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(6);
            lessonsContainer.addView(row, rowLp);
        }

        if (isEditMode) {
            TextView addBtn = createStyledActionButton(
                    "＋ Добавить урок вручную",
                    "#354CAF50", "#8081C784");
            addBtn.setOnClickListener(v -> showStyledLessonDialog(selectedDay, -1));

            LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            addLp.topMargin = dp(4);
            addLp.bottomMargin = dp(6);
            lessonsContainer.addView(addBtn, addLp);

            TextView resetBtn = new TextView(this);
            resetBtn.setText("Очистить всё расписание");
            resetBtn.setTextColor(Color.parseColor("#FFAB91"));
            resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            resetBtn.setGravity(Gravity.CENTER);
            resetBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
            resetBtn.setOnClickListener(v -> {
                ScheduleWidget.resetSchedule(this);
                schedule = ScheduleWidget.loadSchedule(this);
                renderLessons();
                Toast.makeText(this, "Расписание очищено", Toast.LENGTH_SHORT).show();
            });
            lessonsContainer.addView(resetBtn);
        }
    }
    private TextView createStyledActionButton(String text, String fillHex, String strokeHex) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(14), dp(11), dp(14), dp(11));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.parseColor(fillHex));
        bg.setStroke(dp(1), Color.parseColor(strokeHex));
        btn.setBackground(bg);
        return btn;
    }

    private void pickScreenshotFromGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Выбери скриншот расписания"), PICK_IMAGE_REQ);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть галерею", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQ && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            processScreenshotOcr(imageUri);
        }
    }

    private static class OcrLineItem {
        String text;
        int topY;
        OcrLineItem(String text, int topY) {
            this.text = text;
            this.topY = topY;
        }
    }

    private void processScreenshotOcr(Uri uri) {
        Toast.makeText(this, "Распознаю скриншот...", Toast.LENGTH_SHORT).show();
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> parseRecognizedSchedule(visionText))
                    .addOnFailureListener(e -> Toast.makeText(this, "Ошибка чтения картинки", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show();
        }
    }

    private void parseRecognizedSchedule(Text visionText) {
        List<OcrLineItem> lines = new ArrayList<>();
        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                if (line.getBoundingBox() != null) {
                    lines.add(new OcrLineItem(line.getText().trim(), line.getBoundingBox().top));
                }
            }
        }

        lines.sort(Comparator.comparingInt(a -> a.topY));

        Pattern timeRangePattern = Pattern.compile("(\\d{1,2})[:.](\\d{2})\\s*[-–—]\\s*(\\d{1,2})[:.](\\d{2})");
        List<ScheduleWidget.Lesson> parsedLessons = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            OcrLineItem item = lines.get(i);
            Matcher m = timeRangePattern.matcher(item.text);
            if (m.find()) {
                String start = String.format("%02d:%02d", Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                String end = String.format("%02d:%02d", Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));

                String subject = "Урок";
                for (int j = i - 1; j >= 0; j--) {
                    String candidate = lines.get(j).text.trim();
                    if (isValidSubjectLine(candidate)) {
                        subject = fixLatinLookalikes(candidate);
                        break;
                    }
                }
                int num = parsedLessons.size() + 1;
                parsedLessons.add(new ScheduleWidget.Lesson(num, subject, start, end));
            }
        }

        if (parsedLessons.isEmpty()) {
            Toast.makeText(this, "Не найдено время уроков (формат 08:00-08:40)", Toast.LENGTH_LONG).show();
            return;
        }

        if (parsedLessons.get(0).start.isAfter(LocalTime.of(8, 45))) {
            for (int k = 0; k < parsedLessons.size(); k++) {
                parsedLessons.get(k).number = k + 2;
            }
        }

        DayOfWeek targetDay = DAYS[selectedIndex];
        schedule.put(targetDay, parsedLessons);
        ScheduleWidget.saveSchedule(this, schedule);
        renderLessons();
        Toast.makeText(this, "Распознано уроков: " + parsedLessons.size(), Toast.LENGTH_SHORT).show();
    }

    private boolean isValidSubjectLine(String s) {
        if (s.length() < 3) return false;
        String lower = s.toLowerCase();
        if (lower.equals("дз") || lower.equals("расписание") || lower.equals("сегодня")) return false;
        if (lower.matches(".*\\d{1,2}[:.]\\d{2}.*")) return false;
        return true;
    }

    private String fixLatinLookalikes(String raw) {
        String clean = raw.replaceAll("\\s+ДЗ$", "").replaceAll("\\s+H$", "").trim();
        return clean
                .replace('A', 'А').replace('a', 'а')
                .replace('B', 'В').replace('E', 'Е').replace('e', 'е')
                .replace('K', 'К').replace('k', 'к')
                .replace('M', 'М').replace('H', 'Н')
                .replace('O', 'О').replace('o', 'о')
                .replace('P', 'Р').replace('p', 'р')
                .replace('C', 'С').replace('c', 'с')
                .replace('T', 'Т').replace('y', 'у')
                .replace('X', 'Х').replace('x', 'х');
    }

    private void showStyledLessonDialog(DayOfWeek day, int index) {
        List<ScheduleWidget.Lesson> list = schedule.computeIfAbsent(day, k -> new ArrayList<>());
        boolean isNew = (index < 0 || index >= list.size());

        int defNum = isNew ? (list.isEmpty() ? 1 : list.get(list.size() - 1).number + 1) : list.get(index).number;
        String defName = isNew ? "" : list.get(index).name;
        String defStart = isNew ? "08:00" : list.get(index).startStr();
        String defEnd = isNew ? "08:40" : list.get(index).endStr();

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.popup_bg);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView title = new TextView(this);
        title.setText(isNew ? "✨ Новый урок" : "✎ Редактировать урок");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        EditText etName = addStyledField(card, "Название предмета", defName,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        LinearLayout rowInputs = new LinearLayout(this);
        rowInputs.setOrientation(LinearLayout.HORIZONTAL);
        rowInputs.setWeightSum(3f);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(6);
        card.addView(rowInputs, rowLp);

        EditText etNumber = addCompactField(rowInputs, "№ урока", String.valueOf(defNum), InputType.TYPE_CLASS_NUMBER);
        EditText etStart = addCompactField(rowInputs, "Начало", defStart, InputType.TYPE_CLASS_DATETIME);
        EditText etEnd = addCompactField(rowInputs, "Конец", defEnd, InputType.TYPE_CLASS_DATETIME);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = dp(16);
        card.addView(btnRow, btnRowLp);

        if (!isNew) {
            TextView delBtn = createDialogButton("Удалить", "#45F44336", "#90E57373");
            delBtn.setOnClickListener(v -> {
                list.remove(index);
                ScheduleWidget.saveSchedule(this, schedule);
                renderLessons();
                dialog.dismiss();
            });
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            delLp.rightMargin = dp(6);
            btnRow.addView(delBtn, delLp);
        }

        TextView cancelBtn = createDialogButton("Отмена", "#25FFFFFF", "#50FFFFFF");
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cancelLp.rightMargin = dp(6);
        btnRow.addView(cancelBtn, cancelLp);

        TextView saveBtn = createDialogButton("Сохранить", "#554CAF50", "#9081C784");
        saveBtn.setOnClickListener(v -> {
            try {
                int num = Integer.parseInt(etNumber.getText().toString().trim());
                String name = etName.getText().toString().trim();
                String start = normalizeTime(etStart.getText().toString().trim());
                String end = normalizeTime(etEnd.getText().toString().trim());

                if (name.isEmpty()) {
                    Toast.makeText(this, "Введи название предмета", Toast.LENGTH_SHORT).show();
                    return;
                }

                ScheduleWidget.Lesson updated = new ScheduleWidget.Lesson(num, name, start, end);
                if (isNew) {
                    list.add(updated);
                } else {
                    list.set(index, updated);
                }
                list.sort(Comparator.comparing(l -> l.start));
                ScheduleWidget.saveSchedule(this, schedule);
                renderLessons();
                dialog.dismiss();
            } catch (Exception e) {
                Toast.makeText(this, "Формат времени: ЧЧ:ММ (например 08:55)", Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f);
        btnRow.addView(saveBtn, saveLp);

        dialog.setContentView(card);
        if (dialog.getWindow() != null) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (dm.widthPixels * 0.88f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private void showVacationDialog() {
        LocalDate[] currentVac = ScheduleWidget.loadVacation(this);
        LocalDate now = LocalDate.now();
        String defStart = currentVac != null ? formatDayMonth(currentVac[0]) : formatDayMonth(now);
        String defEnd = currentVac != null ? formatDayMonth(currentVac[1]) : formatDayMonth(now.plusDays(7));

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.popup_bg);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView title = new TextView(this);
        title.setText("🏖️ Режим каникул");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Укажи с какого по какое число каникулы (ДД.ММ):");
        desc.setTextColor(Color.parseColor("#B0FFFFFF"));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(4);
        descLp.bottomMargin = dp(8);
        card.addView(desc, descLp);

        LinearLayout rowDates = new LinearLayout(this);
        rowDates.setOrientation(LinearLayout.HORIZONTAL);
        rowDates.setWeightSum(2f);
        card.addView(rowDates);

        EditText etStart = addCompactField(rowDates, "С (день.месяц)", defStart, InputType.TYPE_CLASS_DATETIME);
        EditText etEnd = addCompactField(rowDates, "По (день.месяц)", defEnd, InputType.TYPE_CLASS_DATETIME);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = dp(16);
        card.addView(btnRow, btnRowLp);

        if (currentVac != null) {
            TextView clearBtn = createDialogButton("Отключить", "#45F44336", "#90E57373");
            clearBtn.setOnClickListener(v -> {
                ScheduleWidget.saveVacation(this, null, null);
                updateVacButtonStyle();
                renderTabs();
                Toast.makeText(this, "Режим каникул отключен", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
            LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clearLp.rightMargin = dp(6);
            btnRow.addView(clearBtn, clearLp);
        }

        TextView cancelBtn = createDialogButton("Отмена", "#25FFFFFF", "#50FFFFFF");
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cancelLp.rightMargin = dp(6);
        btnRow.addView(cancelBtn, cancelLp);

        TextView saveBtn = createDialogButton("Сохранить", "#55FF9800", "#90FFB74D");
        saveBtn.setOnClickListener(v -> {
            try {
                LocalDate s = parseDayMonth(etStart.getText().toString().trim());
                LocalDate e = parseDayMonth(etEnd.getText().toString().trim());
                if (e.isBefore(s)) {
                    e = e.plusYears(1);
                }
                ScheduleWidget.saveVacation(this, s, e);
                updateVacButtonStyle();
                renderTabs();
                Toast.makeText(this, "Каникулы сохранены!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (Exception ex) {
                Toast.makeText(this, "Введи дату в формате ДД.ММ (например 28.10)", Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnRow.addView(saveBtn, saveLp);

        dialog.setContentView(card);
        if (dialog.getWindow() != null) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout((int) (dm.widthPixels * 0.88f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private String formatDayMonth(LocalDate d) {
        return String.format("%02d.%02d", d.getDayOfMonth(), d.getMonthValue());
    }

    private LocalDate parseDayMonth(String raw) {
        String clean = raw.replace('/', '.').replace('-', '.').replace(' ', '.');
        String[] parts = clean.split("\\.");
        int day = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int year = LocalDate.now().getYear();
        return LocalDate.of(year, month, day);
    }

    private EditText addStyledField(LinearLayout parent, String labelText, String value, int inputType) {
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Color.parseColor("#B0FFFFFF"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(10);
        labelLp.bottomMargin = dp(4);
        parent.addView(label, labelLp);

        EditText et = new EditText(this);
        et.setText(value);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.parseColor("#60FFFFFF"));
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setPadding(dp(14), dp(10), dp(14), dp(10));

        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setCornerRadius(dp(14));
        fieldBg.setColor(Color.parseColor("#22FFFFFF"));
        fieldBg.setStroke(dp(1), Color.parseColor("#55FFFFFF"));
        et.setBackground(fieldBg);

        parent.addView(et);
        return et;
    }

    private EditText addCompactField(LinearLayout rowParent, String labelText, String value, int inputType) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        boxLp.setMargins(dp(3), 0, dp(3), 0);

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Color.parseColor("#B0FFFFFF"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setGravity(Gravity.CENTER);
        box.addView(label);

        EditText et = new EditText(this);
        et.setText(value);
        et.setTextColor(Color.WHITE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        et.setGravity(Gravity.CENTER);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setPadding(dp(8), dp(9), dp(8), dp(9));

        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setCornerRadius(dp(14));
        fieldBg.setColor(Color.parseColor("#22FFFFFF"));
        fieldBg.setStroke(dp(1), Color.parseColor("#55FFFFFF"));
        et.setBackground(fieldBg);

        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = dp(4);
        box.addView(et, etLp);

        rowParent.addView(box, boxLp);
        return et;
    }

    private TextView createDialogButton(String text, String fillHex, String strokeHex) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(8), dp(11), dp(8), dp(11));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(Color.parseColor(fillHex));
        bg.setStroke(dp(1), Color.parseColor(strokeHex));
        btn.setBackground(bg);
        return btn;
    }

    private String normalizeTime(String raw) {
        String clean = raw.replace('.', ':').replace('-', ':').replace(' ', ':');
        String[] parts = clean.split(":");
        if (parts.length == 2) {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return String.format("%02d:%02d", h, m);
        }
        return raw;
    }

    private int dp(int val) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayM    
