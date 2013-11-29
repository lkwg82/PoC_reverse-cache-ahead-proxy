package de.lgohlke.proxy.ehcache;

import de.lgohlke.proxy.cache.CacheStore;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.bootstrap.BootstrapCacheLoader;

public class CacheStoreAwareBootstrapCacheLoader implements BootstrapCacheLoader {
    private final CacheStore cacheStore;

    public CacheStoreAwareBootstrapCacheLoader(CacheStore cacheStore) {
        this.cacheStore = cacheStore;
    }

    @Override
    public void load(Ehcache cache) throws CacheException {
        cacheStore.load(cache);
    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }

    public Object clone() {
        return null;
    }
}
