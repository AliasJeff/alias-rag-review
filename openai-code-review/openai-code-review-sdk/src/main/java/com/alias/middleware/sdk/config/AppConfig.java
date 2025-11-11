package com.alias.middleware.sdk.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple YAML config loader for application.yml with profile override support.
 * It loads application.yml, reads spring.profiles.active, then overlays application-{profile}.yml.
 */
public final class AppConfig {
    private static final String ROOT_CONFIG = "/application.yml";
    private static final String PROFILED_FORMAT = "/application-%s.yml";

    private static final AppConfig INSTANCE = new AppConfig();

    private final Map<String, Object> merged;

    private AppConfig() {
        Map<String, Object> base = loadYamlAsMap(ROOT_CONFIG);
        String active = getNestedString(base, "spring", "profiles", "active");
        Map<String, Object> profiled = active == null || active.isEmpty()
                ? Collections.emptyMap()
                : loadYamlAsMap(String.format(PROFILED_FORMAT, active));
        this.merged = deepMerge(base, profiled);
    }

    public static AppConfig getInstance() {
        return INSTANCE;
    }

    public String getString(String... path) {
        return getNestedString(this.merged, path);
    }

    public String requireString(String... path) {
        String value = getString(path);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing required config: " + String.join(".", path));
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYamlAsMap(String resourcePath) {
        try (InputStream in = AppConfig.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return new HashMap<>();
            }
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object data = yaml.load(in);
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
            return new HashMap<>();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load config from: " + resourcePath, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = new HashMap<>(base != null ? base : Collections.emptyMap());
        for (Map.Entry<String, Object> entry : override.entrySet()) {
            String key = entry.getKey();
            Object overrideVal = entry.getValue();
            Object baseVal = result.get(key);
            if (baseVal instanceof Map && overrideVal instanceof Map) {
                result.put(key, deepMerge((Map<String, Object>) baseVal, (Map<String, Object>) overrideVal));
            } else {
                result.put(key, overrideVal);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String getNestedString(Map<String, Object> root, String... path) {
        if (root == null) return null;
        Object current = root;
        for (int i = 0; i < path.length; i++) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(path[i]);
            if (current == null) return null;
        }
        return current instanceof String ? (String) current : null;
    }
}


