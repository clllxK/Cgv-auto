package com.local.yongsanimaxwatcher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateUtil {
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");

    public static LocalDate today() {
        return LocalDate.now(KST);
    }

    public static String ymd(LocalDate d) {
        return d.format(YMD);
    }

    public static LocalDate parseYmd(String s) {
        return LocalDate.parse(s, YMD);
    }

    public static String nowStamp() {
        return LocalDateTime.now(KST).format(STAMP);
    }

    public static String pretty(String ymd) {
        LocalDate d = parseYmd(ymd);
        String[] days = {"월", "화", "수", "목", "금", "토", "일"};
        return d.getMonthValue() + "/" + d.getDayOfMonth()
                + "(" + days[d.getDayOfWeek().getValue() - 1] + ")";
    }

    private DateUtil() {}
}
