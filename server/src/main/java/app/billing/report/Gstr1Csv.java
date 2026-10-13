package app.billing.report;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** RFC-style CSV rows for GSTR-1 offline export (UTF-8). */
final class Gstr1Csv {

    private Gstr1Csv() {
    }

    static byte[] toUtf8(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escape(row[i]));
            }
            sb.append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf('"') >= 0 || value.indexOf(',') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    static List<String[]> rows(String[] header, List<String[]> body) {
        List<String[]> all = new ArrayList<>(1 + body.size());
        all.add(header);
        all.addAll(body);
        return all;
    }
}
