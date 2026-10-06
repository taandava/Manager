package com.balajifinance.contactfinder;

import java.util.ArrayList;
import java.util.List;

public class CsvParser {
    public static List<String[]> parse(String text) {
        List<String[]> out = new ArrayList<>();
        if (text == null) return out;
        ArrayList<String> row = new ArrayList<>();
        StringBuilder f = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\"') {
                if (q && i + 1 < text.length() && text.charAt(i + 1) == '\"') {
                    f.append('\"');
                    i++;
                } else {
                    q = !q;
                }
            } else if (c == ',' && !q) {
                row.add(f.toString());
                f.setLength(0);
            } else if ((c == '\n' || c == '\r') && !q) {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(f.toString());
                f.setLength(0);
                if (!(row.size() == 1 && row.get(0).trim().isEmpty())) {
                    out.add(row.toArray(new String[0]));
                }
                row = new ArrayList<>();
            } else {
                f.append(c);
            }
        }
        if (f.length() > 0 || !row.isEmpty()) {
            row.add(f.toString());
            out.add(row.toArray(new String[0]));
        }
        return out;
    }
}
