package com.example.demoproject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class EnhancedPathResolver {

    public static Object read(Object jsonContext, String simplePath) {
        if (simplePath == null || simplePath.trim().isEmpty() || jsonContext == null) return null;

        List<Object> current = new ArrayList<>();
        current.add(jsonContext);

        List<String> segments = splitPath(simplePath);

        for (String seg : segments) {
            String field = seg;
            String predicate = null;
            int idx = seg.indexOf('[');
            if (idx >= 0) {
                field = seg.substring(0, idx);
                predicate = seg.substring(idx + 1, seg.lastIndexOf(']'));
            }

            List<Object> next = new ArrayList<>();
            for (Object ctx : current) {
                if (ctx instanceof Map) {
                    Object val = ((Map<?, ?>) ctx).get(field);
                    if (val == null) continue;
                    if (val instanceof List) {
                        next.addAll((List<?>) val);
                    } else {
                        next.add(val);
                    }
                } else if (ctx instanceof List) {
                    for (Object item : (List<?>) ctx) {
                        if (item instanceof Map) {
                            Object val = ((Map<?, ?>) item).get(field);
                            if (val == null) continue;
                            if (val instanceof List) next.addAll((List<?>) val);
                            else next.add(val);
                        }
                    }
                }
            }

            if (predicate != null) {
                List<Condition> conds = parsePredicate(predicate);
                next = next.stream().filter(n -> matchesAllConditions(n, conds)).collect(Collectors.toList());
            }

            current = next;
            if (current.isEmpty()) break;
        }

        if (current.isEmpty()) return null;
        if (current.size() == 1) return current.get(0);
        return current;
    }

    private static boolean matchesAllConditions(Object node, List<Condition> conditions) {
        if (conditions.isEmpty()) return true;
        if (!(node instanceof Map)) return false;
        Map<?, ?> m = (Map<?, ?>) node;
        for (Condition c : conditions) {
            Object actual = m.get(c.key);
            if (!matchesValue(actual, c.expected)) return false;
        }
        return true;
    }

    private static boolean matchesValue(Object actual, Object expected) {
        if (expected == null) return actual == null;
        if (expected instanceof Boolean) {
            Boolean exp = (Boolean) expected;
            if (actual instanceof Boolean) return exp.equals(actual);
            String s = stringValue(actual);
            return s != null && Boolean.valueOf(s).equals(exp);
        }
        if (expected instanceof Number) {
            if (actual instanceof Number) {
                double a = ((Number) actual).doubleValue();
                double b = ((Number) expected).doubleValue();
                return Double.compare(a, b) == 0;
            }
            String s = stringValue(actual);
            if (s == null) return false;
            try {
                if (expected instanceof Long) {
                    return Long.parseLong(s) == ((Number) expected).longValue();
                } else {
                    return Double.parseDouble(s) == ((Number) expected).doubleValue();
                }
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        // fallback to string comparison
        String a = stringValue(actual);
        return a != null && a.equals(String.valueOf(expected));
    }

    private static String stringValue(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static List<Condition> parsePredicate(String predicate) {
        List<Condition> out = new ArrayList<>();
        String[] parts = predicate.split("\\s+and\\s+");
        for (String p : parts) {
            String[] kv = p.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim();
            String rawVal = kv[1].trim();
            boolean quoted = false;
            if ((rawVal.startsWith("'") && rawVal.endsWith("'")) || (rawVal.startsWith("\"") && rawVal.endsWith("\""))) {
                rawVal = rawVal.substring(1, rawVal.length() - 1);
                quoted = true;
            }
            Object parsed = parseTypedValue(rawVal, quoted);
            out.add(new Condition(key, parsed));
        }
        return out;
    }

    private static Object parseTypedValue(String rawVal, boolean quoted) {
        if (!quoted) {
            String low = rawVal.toLowerCase();
            if ("true".equals(low) || "false".equals(low)) return Boolean.valueOf(low);
            // integer
            if (rawVal.matches("^-?\\d+$")) {
                try {
                    return Long.parseLong(rawVal);
                } catch (NumberFormatException e) {
                    // fallback to double
                }
            }
            // decimal
            if (rawVal.matches("^-?\\d+\\.\\d+$")) {
                try {
                    return Double.parseDouble(rawVal);
                } catch (NumberFormatException e) {
                    // fallback to string
                }
            }
        }
        return rawVal;
    }

    private static List<String> splitPath(String path) {
        List<String> segs = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int bracket = 0;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '[') bracket++;
            if (c == ']') bracket--;
            if (c == '.' && bracket == 0) {
                segs.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) segs.add(cur.toString());
        return segs;
    }

    private static class Condition {
        final String key;
        final Object expected;
        Condition(String k, Object expected) { this.key = k; this.expected = expected; }
    }
}