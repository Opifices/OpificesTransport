package com.client;

import bt.Bt;
import java.lang.reflect.Field;
import java.util.Collection;

public class ReflectionProbe {
    public static void main(String[] args) {
        System.out.println("=== Reflection Probe ===");
        Object builder = Bt.client();
        System.out.println("Builder Class: " + builder.getClass().getName());

        inspect(builder, 0);
    }

    private static void inspect(Object obj, int depth) {
        if (obj == null || depth > 5)
            return;

        Class<?> clazz = obj.getClass();
        String indent = "  ".repeat(depth);

        System.out.println(indent + "Scanning Class: " + clazz.getName());

        // Safety break for cycles (naive)
        if (depth > 2 && clazz.getName().startsWith("java."))
            return;

        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    String type = f.getType().getName();
                    String valStr = (val == null) ? "null" : val.toString();
                    if (valStr.length() > 50)
                        valStr = valStr.substring(0, 47) + "...";

                    System.out.println(indent + " - [FIELD] " + f.getName() + " (" + type + ") = " + valStr);

                    // Recurse into runtimeBuilder
                    if (f.getName().equals("runtimeBuilder") && val != null) {
                        System.out.println(indent + "   >>> Inspecting RuntimeBuilder <<<");
                        inspect(val, depth + 1);
                    }

                    // Inspect potential module collections
                    if (val instanceof Collection && (f.getName().toLowerCase().contains("module"))) {
                        System.out.println(indent + "   >>> Found Module Collection: " + f.getName() + " <<<");
                        inspectCollection((Collection<?>) val, indent + "  ");
                    }

                } catch (Exception e) {
                    System.out.println(indent + " - [FIELD] " + f.getName() + " (ACCESS DENIED)");
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static void inspectCollection(Collection<?> col, String indent) {
        System.out.println(indent + "Collection Size: " + col.size());
        for (Object item : col) {
            System.out.println(indent + " - Item: " + item.getClass().getName());
        }
    }
}
