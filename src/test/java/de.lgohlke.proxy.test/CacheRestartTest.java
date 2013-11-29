package de.lgohlke.proxy.test;

import de.lgohlke.proxy.cache.CacheStore;
import de.lgohlke.proxy.ehcache.CacheStoreAwareBootstrapCacheLoader;
import de.lgohlke.proxy.cache.CacheStoreImpl;
import de.lgohlke.proxy.ehcache.PersistingCacheEventListener;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.event.CacheEventListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.cache.ehcache.EhCacheFactoryBean;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashSet;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class CacheRestartTest {
    private static Logger log = LoggerFactory.getLogger(CacheRestartTest.class);

    @Configuration
    @EnableCaching
    public static class ContextConfig {

        @Bean
        public EhCacheManagerFactoryBean ehCacheManagerFactoryBean() {
            final EhCacheManagerFactoryBean ehCacheManagerFactoryBean = new EhCacheManagerFactoryBean();
            ehCacheManagerFactoryBean.setCacheManagerName(this.getClass().getCanonicalName());
            return ehCacheManagerFactoryBean;
        }

        @Bean
        public EhCacheCacheManager cacheManager(EhCacheManagerFactoryBean ehCacheManagerFactoryBean) {
            EhCacheCacheManager cacheManager = new EhCacheCacheManager();
            cacheManager.setCacheManager(ehCacheManagerFactoryBean.getObject());
            return cacheManager;
        }

        @Bean(name = "bootstrappingCache")
        @Lazy // need lazy to manipulate the CacheStore
        public EhCacheFactoryBean cacheWithBootstrapCacheloader(final CacheStore cacheStore) {
            final EhCacheFactoryBean cache = new EhCacheFactoryBean();
            cache.setCacheName("bootstrappingCache");
            cache.setEternal(true);
            cache.setTimeToLive(0);
            cache.setTimeToIdle(0);

            cache.setBootstrapCacheLoader(new CacheStoreAwareBootstrapCacheLoader(cacheStore));
            return cache;
        }

        @Bean(name = "persistingCache")
        public EhCacheFactoryBean cacheWithShutdownListener(final CacheStore cacheStore) {
            final EhCacheFactoryBean cache = new EhCacheFactoryBean();
            cache.setCacheName("persistingCache");
            cache.setEternal(true);
            cache.setTimeToLive(0);
            cache.setTimeToIdle(0);

            cache.setCacheEventListeners(new HashSet<CacheEventListener>() {{
                add(new PersistingCacheEventListener(cache, cacheStore));
            }});
            return cache;
        }

        @Bean
        public CacheStore cacheStore() {
            return new CacheStoreImpl();
        }
    }

    @Autowired
    private ApplicationContext context;
    @Autowired
    private EhCacheCacheManager ehCacheManager;
    @Autowired
    private CacheStore cacheStore;

    private CacheManager cacheManager;


    @Before
    public void setUp() throws Exception {
        cacheStore.clear();
        // need to create new one 'cause in some tests a shutdown is invoked
        cacheManager = ehCacheManager.getCacheManager().create();
    }

    @Test
    public void testPersistingCacheWithCacheEventListener() {

        Cache cache = cacheManager.getCache("persistingCache");
        cache.put(new Element("x", "x"));

        cacheManager.shutdown();

        assertThat(cacheStore.getElements()).hasSize(1);
    }

    @Test
    public void testBootstrappingCacheWithBootstrapCacheLoader() {

        cacheStore.getElements().add(new Element("test", "test"));

        // invoking lazy loaded bean
        context.getBean("bootstrappingCache");

        assertThat(cacheStore.getElements()).hasSize(1);

        Cache cache = cacheManager.getCache("bootstrappingCache");

        assertThat(cache.getKeys()).hasSize(1);
    }
}
