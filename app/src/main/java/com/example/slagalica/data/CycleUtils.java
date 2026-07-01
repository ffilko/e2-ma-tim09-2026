package com.example.slagalica.data;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class CycleUtils {

    private CycleUtils() {}

    private static final Locale SR = new Locale("sr", "RS");

    private static Calendar weekStart(Date date) {
        Calendar c = Calendar.getInstance(SR);
        c.setTime(date);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        int diff = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        c.add(Calendar.DAY_OF_MONTH, diff);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private static String weekKey(Calendar weekStart) {
        return new SimpleDateFormat("yyyy-MM-dd", SR).format(weekStart.getTime());
    }

    public static String currentWeekKey() {
        return weekKey(weekStart(new Date()));
    }

    public static String previousWeekKey() {
        Calendar c = weekStart(new Date());
        c.add(Calendar.DAY_OF_MONTH, -7);
        return weekKey(c);
    }

    public static String weekRangeLabel(String weekKey) {
        try {
            Date monday = new SimpleDateFormat("yyyy-MM-dd", SR).parse(weekKey);
            Calendar c = Calendar.getInstance(SR);
            c.setTime(monday);
            SimpleDateFormat dm = new SimpleDateFormat("dd.MM.", SR);
            String start = dm.format(c.getTime());
            c.add(Calendar.DAY_OF_MONTH, 6);
            SimpleDateFormat dmy = new SimpleDateFormat("dd.MM.yyyy.", SR);
            String end = dmy.format(c.getTime());
            return start + " - " + end;
        } catch (Exception e) {
            return weekKey;
        }
    }

    private static String monthKey(Calendar c) {
        return new SimpleDateFormat("yyyy-MM", SR).format(c.getTime());
    }

    public static String currentMonthKey() {
        return monthKey(Calendar.getInstance(SR));
    }

    public static String previousMonthKey() {
        Calendar c = Calendar.getInstance(SR);
        c.add(Calendar.MONTH, -1);
        return monthKey(c);
    }

    public static String monthRangeLabel(String monthKey) {
        try {
            Calendar c = Calendar.getInstance(SR);
            c.setTime(new SimpleDateFormat("yyyy-MM", SR).parse(monthKey));
            c.set(Calendar.DAY_OF_MONTH, 1);
            SimpleDateFormat dm = new SimpleDateFormat("dd.MM.", SR);
            String start = dm.format(c.getTime());
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH));
            SimpleDateFormat dmy = new SimpleDateFormat("dd.MM.yyyy.", SR);
            String end = dmy.format(c.getTime());
            return start + " - " + end;
        } catch (Exception e) {
            return monthKey;
        }
    }

    public static String leagueName(int league) {
        switch (league) {
            case 1: return "Bronzana";
            case 2: return "Srebrna";
            case 3: return "Zlatna";
            case 4: return "Platinasta";
            case 5: return "Dijamantska";
            default: return "Početnik";
        }
    }
}
