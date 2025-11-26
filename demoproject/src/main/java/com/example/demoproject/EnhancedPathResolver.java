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
            Object actual = getValueByPath(m, c.key);
            if (!matchesWithOperator(actual, c.operator, c.expected)) return false;
        }
        return true;
    }

    private static Object getValueByPath(Object node, String keyPath) {
        if (node == null || keyPath == null || keyPath.isEmpty()) return null;
        String[] parts = keyPath.split("\\.");
        Object cur = node;
        for (String p : parts) {
            if (cur == null) return null;
            if (cur instanceof Map) {
                cur = ((Map<?, ?>) cur).get(p);
            } else if (cur instanceof List) {
                // if it's a list, we can't resolve a nested property directly;
                // try to find the property on any element and return the first match
                Object found = null;
                for (Object item : (List<?>) cur) {
                    if (item instanceof Map) {
                        Object v = ((Map<?, ?>) item).get(p);
                        if (v != null) { found = v; break; }
                    }
                }
                cur = found;
            } else {
                // primitive encountered before finishing path
                return null;
            }
        }
        return cur;
    }

    private static boolean matchesWithOperator(Object actual, String operator, Object expected) {
        if (operator == null || operator.isEmpty()) operator = "=";
        if (expected == null) {
            boolean eq = actual == null;
            return "!=".equals(operator) ? !eq : eq;
        }

        // Boolean handling
        if (expected instanceof Boolean) {
            Boolean exp = (Boolean) expected;
            Boolean act = null;
            if (actual instanceof Boolean) act = (Boolean) actual;
            else {
                String s = stringValue(actual);
                if (s != null) act = Boolean.valueOf(s);
            }
            if (act == null) return false;
            return "!=".equals(operator) ? !exp.equals(act) : exp.equals(act);
        }

        // Numeric handling
        if (expected instanceof Number) {
            Double expD = ((Number) expected).doubleValue();
            Double actD = null;
            if (actual instanceof Number) actD = ((Number) actual).doubleValue();
            else {
                String s = stringValue(actual);
                if (s != null) {
                    try { actD = Double.parseDouble(s); } catch (NumberFormatException e) { return false; }
                }
            }
            if (actD == null) return false;
            switch (operator) {
                case "!":
                case "!=": return Double.compare(actD, expD) != 0;
                case ">": return Double.compare(actD, expD) > 0;
                case "<": return Double.compare(actD, expD) < 0;
                case ">=": return Double.compare(actD, expD) >= 0;
                case "<=": return Double.compare(actD, expD) <= 0;
                default: return Double.compare(actD, expD) == 0;
            }
        }

        // String/fallback handling
        String a = stringValue(actual);
        String e = String.valueOf(expected);
        if (a == null) return false;
        switch (operator) {
            case "!":
            case "!=": return !a.equals(e);
            case ">": return a.compareTo(e) > 0;
            case "<": return a.compareTo(e) < 0;
            case ">=": return a.compareTo(e) >= 0;
            case "<=": return a.compareTo(e) <= 0;
            default: return a.equals(e);
        }
    }

    private static String stringValue(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static List<Condition> parsePredicate(String predicate) {
        List<Condition> out = new ArrayList<>();
        String[] parts = predicate.split("\\s+and\\s+");
        for (String p : parts) {
            String expr = p.trim();
            String operator = null;
            String key = null;
            String rawVal = null;
            // check for multi-char operators first
            String[] ops = {"!=", ">=", "<=", ">", "<", "=", "=="};
            for (String op : ops) {
                int pos = expr.indexOf(op);
                if (pos > 0) {
                    operator = op.equals("==") ? "=" : op;
                    key = expr.substring(0, pos).trim();
                    rawVal = expr.substring(pos + op.length()).trim();
                    break;
                }
            }
            if (operator == null || key == null || rawVal == null) continue;
            boolean quoted = false;
            if ((rawVal.startsWith("'") && rawVal.endsWith("'")) || (rawVal.startsWith("\"") && rawVal.endsWith("\""))) {
                rawVal = rawVal.substring(1, rawVal.length() - 1);
                quoted = true;
            }
            Object parsed = parseTypedValue(rawVal, quoted);
            out.add(new Condition(key, operator, parsed));
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
        final String operator;
        final Object expected;
        Condition(String k, String operator, Object expected) { this.key = k; this.operator = operator; this.expected = expected; }
    }
}