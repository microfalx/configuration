# Configuration

A lightweight, unified abstraction over multiple process configuration sources.

---

## Introduction

Many applications need to read runtime configuration from a variety of places: JVM system properties,
OS environment variables, a central registry, a database, or a custom store. Wiring all those sources
together and exposing a single, consistent API is tedious boilerplate.

**Configuration** solves this by providing:

* A **unified `Configuration` interface** — typed accessors (`String`, `int`, `long`, `boolean`, `double`,
  `float`, or any arbitrary type via generics) and a single `set()` method that persists changes.
* A **pluggable `ConfigurationSource` SPI** — swap or layer backing stores without touching consumer code.
* A **hierarchical tree with `Subset`s** — logically partition a flat key-space into labelled groups
  that mirror the way settings are presented in a UI.
* A **metadata model** — describe every configuration entry (display name, data type, default value,
  valid range, read-only flag, …) through a compact XML descriptor that is discovered automatically
  from the classpath.
* A **change-notification mechanism** — listeners receive strongly-typed events whenever a single
  property or an entire group is updated.

This is conceptually similar to
[Apache Commons Configuration](https://commons.apache.org/proper/commons-configuration/), but the API
surface is intentionally smaller and the integration points are reduced to what most applications
actually need.

---

## Implementation

### `ConfigurationSource` — the plug-in point

```java
public interface ConfigurationSource {
    String getProperty(String key);
}
```

`ConfigurationSource` is the single extension point that adapts any external data store to the
library.  A `null` return value is normalised to an empty string so that consumers never need to
null-check lookup results.

`AbstractConfigurationSource` is the recommended base class.  It provides **key normalisation**,
which makes the following three forms of the same property equivalent:

| Written as | Stored as |
|---|---|
| `camelCase` | `camelcase` |
| `camel-case` | `camelcase` |
| `camel_case` | `camelcase` |

The built-in `JvmConfigurationSource` demonstrates this pattern: it eagerly copies all JVM system
properties _and_ OS environment variables into a normalised map at construction time.

To plug in a different back-end (e.g. a database, Vault, or a remote configuration server) simply
implement `ConfigurationSource` (or extend `AbstractConfigurationSource`) and call
`ConfigurationService.setConfigurationSource(…)`.

---

### `Configuration` — the unified read/write API

```java
public interface Configuration extends ConfigurationListenerAware {
    Set<String> getKeys();

    String  get(String key);
    String  get(String key, String defaultValue);
    boolean get(String key, boolean defaultValue);
    int     get(String key, int defaultValue);
    long    get(String key, long defaultValue);
    float   get(String key, float defaultValue);
    double  get(String key, double defaultValue);
    <T> T   get(String key, Class<T> type, Object defaultValue);

    <T> void set(String key, T value);

    Configuration getParent();
    Subset at(String prefix);
}
```

Every `get()` overload accepts a **default value** that is returned when the key is absent, keeping
call sites free of null checks.  The generic `get(key, Class<T>, defaultValue)` overload handles
arbitrary type conversions through the `Language Extensions` project [type converter](https://github.com/microfalx/lang/blob/33ae8f903fcdc588f2523bb352735400351db991/src/main/java/net/microfalx/lang/convert/Types.java).

`set()` is persistent: the value is written back to the underlying registry and all registered
`ConfigurationListener`s are notified.

`at(prefix)` creates a `Subset` — a lazily-scoped view rooted at the given dot-separated prefix.

---

### `Subset` — named partitions of the tree

```java
public interface Subset extends Configuration {
    String getTitle();
    String getPrefix();
}
```

A `Subset` is a full `Configuration` whose keys are automatically relative to its prefix.  Subsets
can be nested arbitrarily deep and carry a human-readable `title` that can be used in UIs.

---

### `Metadata` — declarative entry descriptors

`Metadata` describes a single configuration entry or group:

| Property | Description |
|---|---|
| `key` / `fullKey` | Dot-separated path (e.g. `group1.group12.item1`) |
| `name` | Human-readable display name |
| `description` | Optional longer description |
| `dataType` | `STRING`, `INTEGER`, `NUMBER`, `BOOLEAN`, or `DURATION` |
| `defaultValue` | Value used when no explicit value is stored |
| `minimum` / `maximum` | Valid range for numeric types |
| `required` / `readOnly` / `client` | Behavioural flags |
| `multiline` / `lineCount` | Hint for multi-line text fields |
| `section` | Optional grouping label within a group |

Metadata objects form a tree (parent → children) that mirrors the key hierarchy and is built by
`ConfigurationLoader` from XML descriptor files (see [Usage](#usage) below).

---

### `ConfigurationService` — the orchestrator

`ConfigurationService` is the central singleton that wires everything together:

* Holds the active `ConfigurationSource` (defaults to `JvmConfigurationSource`).
* Owns the root `Configuration` / `Subset` tree.
* Builds and owns the `Metadata` registry from classpath descriptors on start-up.
* Provides a short-lived (5 s by default) **read-through cache** to reduce registry round-trips.
* Reads from (and writes to) a `Registry` for persistence.
* Transparently handles **encrypted secrets** — values stored for keys that look like secrets are
  encrypted at rest and decrypted on read.
* Fires `ConfigurationEvent`s to registered `ConfigurationListener`s on every change.

---

### `ConfigurationLoader` — XML descriptor parsing

`ConfigurationLoader` scans the classpath for `configuration.xml` descriptor files (discovered via
`ConfigurationUtils.getDescriptors()`).  Each descriptor declares groups and items that populate the
`Metadata` tree used by `ConfigurationService`.

---

### `ConfigurationListener` / `ConfigurationEvent` — change notifications

```java
public interface ConfigurationListener {
    void onEvent(ConfigurationEvent event);
}
```

`ConfigurationEvent` carries the affected `Configuration`, the event `Type` (`PROPERTY` or `GROUP`),
the key, and (for property events) the previous and current values.  Convenience matchers
`matchesProperties(prefix)` and `matchesGroup(prefix)` make filtering easy.

---

## Usage

### 1. Reading configuration values

```java
Configuration config = Configuration.get();

// String — empty string if absent
String host = config.get("app.server.host");

// Typed with a default
int    port    = config.get("app.server.port",    8080);
boolean secure = config.get("app.server.secure",  false);
long   timeout = config.get("app.server.timeout", 30_000L);

// Generic type conversion
Integer workers = config.get("app.thread.pool.workers", Integer.class, 4);
```

### 2. Writing a value

```java
Configuration config = Configuration.get();
config.set("app.server.port", 9090);
// The change is persisted and listeners are notified automatically.
```

### 3. Working with subsets

```java
Configuration config = Configuration.get();
// Scope to a sub-tree — keys are relative to the prefix
Subset serverConfig = config.at("app.server");
String host = serverConfig.get("host");   // resolves app.server.host
int    port  = serverConfig.get("port", 8080);

// Subsets carry a title useful for UI rendering
System.out.println(serverConfig.getTitle());   // e.g. "Server"
System.out.println(serverConfig.getPrefix());  // "app.server"
```

### 4. Plugging in a custom `ConfigurationSource`

```java
public class MyDatabaseSource extends AbstractConfigurationSource {
    @Override
    public String getProperty(String key) {
        // normalize() maps camelCase / kebab-case / snake_case to the same key
        return myDb.findValue(normalize(key));
    }
}

ConfigurationService service = ConfigurationService.get();
service.setConfigurationSource(new MyDatabaseSource());
```

Key normalisation means `camelCase`, `camel-case`, and `camel_case` all resolve to the same entry:

```java
configurationSource.setProperty("camelCase", 1);
assertEquals("1", service.getProperty("camelCase"));  // ✓
assertEquals("1", service.getProperty("camel-case")); // ✓
assertEquals("1", service.getProperty("camel_case")); // ✓
```

### 5. Listening for changes

```java
service.addListener(event -> {
    if (event.matchesProperties("app.server")) {
        System.out.printf("Property %s changed: %s → %s%n",
            event.getKey(), event.getPreviousValue(), event.getCurrentValue());
    }
    if (event.matchesGroup("app")) {
        System.out.println("The 'app' group was reloaded");
    }
});
```

### 6. Declaring metadata via XML descriptor

Place a `configuration.xml` on the classpath (e.g. `src/main/resources/configuration.xml`) to
register descriptive metadata for your configuration keys:

```xml
<?xml version="1.0" encoding="ISO-8859-1"?>
<configuration xmlns="https://net.microfalx/xsd/configuration-1.0.xsd">

    <group key="group1" name="Group 1">

        <!-- Nested group with a description -->
        <group key="group12" name="Group 1-2" description="Group 1 description">
            <item key="item1" name="Item 1" default="20" description="Item 1 description"/>
            <item key="item2" name="Item 2"
                  data-type="number" minimum="1.5" maximum="5.5"
                  default="2.3" description="Item 2 description"/>
        </group>

        <!-- Integer item with a valid range -->
        <item key="item1" name="Item 1"
              data-type="integer" minimum="0" maximum="100"
              default="20" description="Item 1 description"/>
    </group>

    <group key="group2" name="Group 2"/>

</configuration>
```

`ConfigurationService` discovers and loads all such descriptors at start-up, registers every item in
the registry, and makes them available through `getMetadata(key)` and `getEntries(prefix)`:

```java
// Traverse the metadata tree
Metadata root = service.getRootMetadata();
root.getChildren().forEach(group -> {
    System.out.println(group.getName());
    group.getChildren().forEach(item ->
        System.out.println("  " + item.getFullKey() + " = " + item.getDefaultValue()));
});

// Look up a specific entry
Metadata item = service.getMetadata("group1.group12.item1");
System.out.println(item.getDataType());    // STRING
System.out.println(item.getDefaultValue()); // 20
```
