package com.dukaansaathi.voice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a reply into text that a voice reads correctly. The speech service reads "38" digit by digit and drops the ₹ sign,
 * so amounts and numbers are written out in words ("₹1,173" becomes "one thousand one hundred seventy three rupees").
 */
public final class SpeechText {
    private static final int MAX_CHARS = 700;
    private static final String[] SMALL = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
            "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};

    /** A masked phone number in brackets, such as (9XXXX85080), is useless when spoken. */
    private static final Pattern PHONE = Pattern.compile("\\s*\\([6-9Xx]\\w{8,10}\\)");
    /** Digits grouped by commas ("1,00,000"); a comma that only separates items in a list ("500, Sita") is not part of the number. */
    private static final Pattern RUPEES = Pattern.compile("(-?)₹\\s?(\\d+(?:,\\d{2,3})*)(?:\\.(\\d{1,2}))?");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])(-?)(\\d+(?:,\\d{2,3})*)(?:\\.(\\d+))?(%)?(?!\\w)");
    private static final Pattern DATE = Pattern.compile("(?<![\\w.])(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})(?!\\w|\\.\\d)");

    private SpeechText() {}

    public static String forSpeech(String text) {
        if (text == null) return "";
        String s = PHONE.matcher(text).replaceAll("");
        s = replace(RUPEES, s, (m) -> {
            StringBuilder out = new StringBuilder(m.group(1).isEmpty() ? "" : "minus ");
            out.append(words(m.group(2))).append(" rupees");
            if (m.group(3) != null) {
                String paise = m.group(3).length() == 1 ? m.group(3) + "0" : m.group(3);
                if (!paise.equals("00")) out.append(' ').append(words(paise)).append(" paise");
            }
            return out.toString();
        });
        s = replace(DATE, s, (m) -> words(m.group(1)) + " " + words(m.group(2)) + " " + words(m.group(3)));
        s = replace(NUMBER, s, (m) -> {
            String out = (m.group(1).isEmpty() ? "" : "minus ") + words(m.group(2));
            if (m.group(3) != null) out += " point " + digits(m.group(3));
            return m.group(4) != null ? out + " percent" : out;
        });
        s = s.replace("→", " to ").replaceAll("[•\\n]+", ". ").replaceAll("\\.\\s*\\.", ".").replaceAll("[ \\t]+", " ").trim();
        return cut(s);
    }

    /** The whole number written out the Indian way: lakh and crore. Very long numbers are read digit by digit. */
    static String words(String number) {
        String clean = number.replace(",", "");
        if (clean.length() > 9) return digits(clean);
        long n = Long.parseLong(clean);
        if (n == 0) return "zero";
        StringBuilder out = new StringBuilder();
        append(out, n / 10_000_000, "crore");
        append(out, n / 100_000 % 100, "lakh");
        append(out, n / 1_000 % 100, "thousand");
        append(out, n / 100 % 10, "hundred");
        long last = n % 100;
        if (last > 0) out.append(out.length() > 0 ? " " : "").append(below100(last));
        return out.toString();
    }

    private static void append(StringBuilder out, long count, String unit) {
        if (count == 0) return;
        out.append(out.length() > 0 ? " " : "").append(below100(count)).append(' ').append(unit);
    }

    private static String below100(long n) {
        if (n < 20) return SMALL[(int) n];
        return TENS[(int) n / 10] + (n % 10 == 0 ? "" : " " + SMALL[(int) n % 10]);
    }

    private static String digits(String digits) {
        StringBuilder out = new StringBuilder();
        for (char c : digits.toCharArray()) {
            if (!Character.isDigit(c)) continue;
            out.append(out.length() > 0 ? " " : "").append(SMALL[c - '0']);
        }
        return out.toString();
    }

    /** The first MAX_CHARS characters, ended at a sentence if there is one, so a long list is not read out in full. */
    private static String cut(String s) {
        if (s.length() <= MAX_CHARS) return s;
        String head = s.substring(0, MAX_CHARS);
        int end = Math.max(head.lastIndexOf(". "), head.lastIndexOf('?'));
        return (end > MAX_CHARS / 2 ? head.substring(0, end + 1) : head).trim();
    }

    private static String replace(Pattern pattern, String input, java.util.function.Function<Matcher, String> with) {
        Matcher m = pattern.matcher(input);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(with.apply(m)));
        m.appendTail(out);
        return out.toString();
    }
}
