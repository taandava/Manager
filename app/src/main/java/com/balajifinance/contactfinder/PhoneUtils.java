package com.balajifinance.contactfinder;

import java.util.Locale;

public class PhoneUtils {
    public static String normalizePhone(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        String digits = trimmed.replaceAll("[^0-9+]", "");
        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
        }
        if (digits.matches("[0-9]{10}")) {
            digits = "+91" + digits;
        } else if (digits.matches("91[0-9]{10}")) {
            digits = "+" + digits;
        }
        return digits.isEmpty() ? trimmed : digits;
    }

    public static String formatIndianPhone(String phone) {
        String norm = normalizePhone(phone);
        if (norm.matches("\\+91[0-9]{10}")) {
            String digits = norm.substring(3);
            return "+91 " + digits.substring(0, 5) + " " + digits.substring(5, 10);
        }
        if (norm.matches("[0-9]{10}")) {
            return norm.substring(0, 5) + " " + norm.substring(5, 10);
        }
        return phone != null ? phone : "";
    }

    public static String normalizeSearch(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
