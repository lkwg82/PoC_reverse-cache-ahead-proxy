package de.lgohlke.proxy.ehcache;

import de.lgohlke.proxy.cache.CacheStore;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.event.CacheEventListenerAdapter;
import org.springframework.cache.ehcache.EhCacheFactoryBean;

public class PersistingCacheEventListener extends CacheEventListenerAdapter {
    private final EhCacheFactoryBean ehCacheFactoryBean;
    private final CacheStore cacheStore;

    public PersistingCacheEventListener(EhCacheFactoryBean ehCacheFactoryBean, CacheStore cacheStore) {
        this.ehCacheFactoryBean = ehCacheFactoryBean;
        this.cacheStore = cacheStore;
    }

    private Ehcache cache() {
        return ehCacheFactoryBean.getObject();
    }

    @Override
    public void dispose() {
        super.dispose();
        cacheStore.save(cache());
    }
}
