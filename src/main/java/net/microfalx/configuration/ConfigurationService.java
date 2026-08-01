package net.microfalx.configuration;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.*;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.convert.Types;
import net.microfalx.lang.service.Service;
import net.microfalx.registry.Data;
import net.microfalx.registry.Registry;
import net.microfalx.threadpool.ThreadPool;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.unmodifiableCollection;
import static net.microfalx.configuration.ConfigurationUtils.REGISTRY_PATH;
import static net.microfalx.configuration.ConfigurationUtils.ROOT_METADATA_ID;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.StringUtils.*;
import static net.microfalx.lang.TimeUtils.millisSince;

@SuppressWarnings("unchecked")
@Slf4j
@Provider
public class ConfigurationService implements Service, Initializable {

    private Configuration configuration;

    private Registry registry;
    private ThreadPool threadPool = ThreadPool.get();
    private Duration cacheExpiration = Duration.ofSeconds(5);
    private final Map<String, Metadata> metadatas = new ConcurrentHashMap<>();
    private final Map<String, CachedValue> cachedValues = new ConcurrentHashMap<>();
    private final Collection<ConfigurationListener> listeners = new CopyOnWriteArrayList<>();

    private volatile ConfigurationSource configurationSource = new JvmConfigurationSource();

    /**
     * Returns the registry used to store the configuration.
     *
     * @return a non-null instance
     */
    public Registry getRegistry() {
        if (registry == null) {
            registry = Registry.get();
        }
        return registry;
    }

    /**
     * Returns the active configuration source.
     *
     * @return a non-null instance
     */
    public ConfigurationSource getConfigurationSource() {
        return configurationSource;
    }

    /**
     * Changes the configuration source.
     *
     * @param configurationSource the new source.
     */
    public void setConfigurationSource(ConfigurationSource configurationSource) {
        requireNonNull(configurationSource);
        this.configurationSource = configurationSource;
    }

    /**
     * Returns the root configuration.
     *
     * @return a non-null instance
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Registers a configuration listener.
     *
     * @param listener a non-null instance
     */
    public void addListener(ConfigurationListener listener) {
        requireNonNull(listener);
        listeners.add(listener);
    }

    /**
     * Returns an application property
     *
     * @param key the name of the property
     * @return the value
     */
    public String getProperty(String key) {
        requireNonNull(key);
        try {
            return emptyIfNull(configurationSource.getProperty(key));
        } catch (Exception e) {
            LOGGER.warn("Failed to get the property '{}', root cause: {}", key, getRootCauseDescription(e));
            return EMPTY_STRING;
        }
    }

    /**
     * Returns the root metadata.
     *
     * @return a non-null instance
     */
    public Metadata getRootMetadata() {
        return metadatas.get(toIdentifier(ROOT_METADATA_ID));
    }

    /**
     * Returns metadata for the given key.
     *
     * @param key the configuration key
     * @return a non-null instance
     */
    public Metadata getMetadata(String key) {
        requireNonNull(key);
        Metadata metadata = this.metadatas.get(toIdentifier(key));
        if (metadata == null) {
            metadata = new Metadata(null, key, ConfigurationUtils.getTitle(key));
            this.metadatas.put(metadata.getId(), metadata);
        }
        return metadata;
    }

    /**
     * Returns all registered entries with a given prefix
     *
     * @param prefix the prefix
     * @return a non-null instance
     */
    public Collection<Metadata> getEntries(String prefix) {
        if (isEmpty(prefix)) {
            return unmodifiableCollection(this.metadatas.values());
        } else {
            String idPrefix = toIdentifier(prefix);
            return this.metadatas.values().stream()
                    .filter(Metadata::isLeaf)
                    .filter(metadata -> metadata.getId().startsWith(idPrefix))
                    .toList();
        }

    }

    /**
     * Registers metadata associated with the configuration entry.
     *
     * @param metadata the metadata to register
     */
    public void registerMetadata(Metadata metadata) {
        requireNonNull(metadata);
        this.metadatas.put(metadata.getId(), metadata);
    }

    /**
     * Notifies listeners that a group (all properties under the group) changed.
     *
     * @param metadata the metadata of the group
     */
    public void notifyGroupChange(Metadata metadata) {
        requireNonNull(metadata);
        clearCache();
        ConfigurationEvent event = new ConfigurationEvent(configuration, ConfigurationEvent.Type.GROUP, metadata.getFullKey());
        fireConfigurationEvent(event);
    }

    @Override
    public void initialize(Object... context) {
        configuration = new SubsetImpl(this, null, EMPTY_STRING, "Root");
        loadMetadata();
        threadPool.execute(this::registerMetadata);
    }

    /**
     * Clears the caches associated with the configuration.
     */
    public void clearCache() {
        cachedValues.clear();
    }

    void propertyChanged(Configuration configuration, String key, String previousValue, String currentValue) {
        ConfigurationEvent event = new ConfigurationEvent(configuration, ConfigurationEvent.Type.PROPERTY, key, previousValue, currentValue);
        fireConfigurationEvent(event);
    }

    <T> T convert(String key, Object value, Class<T> type) {
        if (ObjectUtils.isEmpty(value) && type.isPrimitive()) {
            value = createDefault(type);
        }
        if (value == null) return null;
        try {
            return Types.from(value, type);
        } catch (Exception e) {
            throw new ConfigurationException("Failed to convert value '" + value + "' to "
                    + ClassUtils.getName(type) + " for key '" + key + "'", e);
        }
    }

    String getFromRegistry(Configuration configuration, String key, String defaultValue) {
        String value = getFromCache(key);
        if (isEmpty(value)) {
            String registryKey = getRegistryPath(key);
            Optional<Data> data = getRegistry().get(registryKey);
            if (data.isPresent()) {
                value = ObjectUtils.toString(data.get().get());
            } else {
                value = getProperty(key);
            }
            cachedValues.put(key, new CachedValue(value));
        }
        if (SecretUtils.isSecret(key) && EncryptionUtils.isEncrypted(value)) {
            value = EncryptionUtils.decrypt(value);
        }
        return defaultIfNull(value, defaultValue);
    }

    void setToRegistry(Configuration configuration, String key, Object value) {
        String registryKey = getRegistryPath(key);
        Data data = getRegistry().getOrCreate(registryKey);
        String previousValue = ObjectUtils.toString(data.get());
        data.set(value);
        getRegistry().set(data);
        propertyChanged(configuration, key, previousValue, ObjectUtils.toString(value));
    }

    private void loadMetadata() {
        ConfigurationLoader loader = new ConfigurationLoader();
        loader.load();
        this.metadatas.putAll(loader.getMetadata());
        LOGGER.info("Loaded {} configuration groups with {} items from {} resources", loader.getGroupCount(),
                loader.getItemCount(), loader.getResourceCount());
    }

    private void registerMetadata() {
        Registry registry = getRegistry();
        int registered = 0;
        for (Metadata metadata : metadatas.values()) {
            try {
                if (registerMetadata(registry, metadata)) registered++;
            } catch (Exception e) {
                LOGGER.atError().setCause(e).log("Failed to register metadata {} in registry", metadata.getFullKey());
            }
        }
        LOGGER.info("Registered {} new configuration entries in registry", registered);
    }

    private boolean registerMetadata(Registry registry, Metadata metadata) {
        Data data = registry.getOrCreate(getRegistryPath(metadata.getFullKey()));
        if (data.exists() || !metadata.isLeaf()) return false;
        boolean isSecret = SecretUtils.isSecret(metadata.getFullKey());
        data.setAttribute("key", metadata.getFullKey());
        data.setAttribute("name", metadata.getName());
        String value = getProperty(metadata.getFullKey());
        if (isEmpty(value)) value = metadata.getDefaultValue();
        value = isSecret && !EncryptionUtils.isEncrypted(value) ? EncryptionUtils.encrypt(value) : value;
        data.set(value);
        registry.set(data);
        return true;
    }

    private String getFromCache(String key) {
        CachedValue cachedValue = cachedValues.get(key);
        if (cachedValue != null && !cachedValue.isExpired(cacheExpiration)) {
            return cachedValue.getValue();
        } else {
            return null;
        }
    }

    private String getRegistryPath(String key) {
        String path = null;
        Metadata metadata = getMetadata(key);
        if (metadata != null) {
            Metadata parentMetadata = metadata.getParent();
            if (parentMetadata != null) {
                path = toIdentifier(parentMetadata.getFullKey()) + "/" + toIdentifier(metadata.getKey());
            }
        }
        if (path == null) {
            int index = key.lastIndexOf('.');
            if (index >= 0) {
                path = toIdentifier(key.substring(0, index)) + "/" + toIdentifier(key.substring(index + 1));
            } else {
                path = toIdentifier(key);
            }
        }
        return REGISTRY_PATH + "/" + path;
    }

    void fireConfigurationEvent(ConfigurationEvent event) {
        for (ConfigurationListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T createDefault(Class<?> type) {
        Object value = defaultValues.get(type);
        if (value != null) {
            return (T) value;
        } else {
            return null;
        }
    }

    @Getter
    @ToString
    private static class CachedValue {

        private final String value;
        private final long created = currentTimeMillis();

        private CachedValue(String value) {
            this.value = value;
        }

        boolean isExpired(Duration expiration) {
            return millisSince(created) > expiration.toMillis();
        }
    }

    private static final Map<Class<?>, Object> defaultValues = new HashMap<>();

    static {
        defaultValues.put(boolean.class, false);
        defaultValues.put(byte.class, (byte) 0);
        defaultValues.put(short.class, (short) 0);
        defaultValues.put(int.class, 0);
        defaultValues.put(long.class, 0L);
        defaultValues.put(float.class, 0f);
        defaultValues.put(double.class, 0d);
        defaultValues.put(Duration.class, Duration.ofSeconds(30));
    }
}
