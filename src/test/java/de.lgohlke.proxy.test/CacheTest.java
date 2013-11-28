package de.lgohlke.proxy.test;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.constructs.refreshahead.RefreshAheadCache;
import net.sf.ehcache.constructs.refreshahead.RefreshAheadCacheConfiguration;
import net.sf.ehcache.loader.CacheLoader;
import net.sf.ehcache.loader.CacheLoaderFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.cache.ehcache.EhCacheFactoryBean;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class CacheTest {
    private static Logger log = LoggerFactory.getLogger(CacheTest.class);

    // http://terracotta.org/documentation/4.0/bigmemorymax/api/refresh-ahead

    @Configuration
    @EnableCaching
    public static class ContextConfig {

        @Bean
        public EhCacheManagerFactoryBean ehCacheManagerFactoryBean() {
            return new EhCacheManagerFactoryBean();
        }

        @Bean
        public CacheManager cacheManager(EhCacheManagerFactoryBean ehCacheManagerFactoryBean) {
            EhCacheCacheManager cacheManager = new EhCacheCacheManager();
            cacheManager.setCacheManager(ehCacheManagerFactoryBean.getObject());
            return cacheManager;
        }

        @Bean
        public EhCacheFactoryBean cache() {
            EhCacheFactoryBean cache = new EhCacheFactoryBean();
            cache.setCacheName("test");
            cache.setEternal(true);
            return cache;
        }

        @Bean
        public RefreshAheadCacheConfiguration refreshAheadCacheConfiguration() {
            RefreshAheadCacheConfiguration configuration = new RefreshAheadCacheConfiguration();
            configuration.setMaximumRefreshBacklogItems(100);
            configuration.setNumberOfThreads(2);
            configuration.setTimeToRefreshSeconds(2);
            configuration.build();
            return configuration;
        }

        @Bean
        public RefreshAheadCache refreshAheadCache(CacheManager cacheManager, RefreshAheadCacheConfiguration configuration, CacheLoaderFactory cacheLoaderFactory) {
            Object cache = cacheManager.getCache("test").getNativeCache();
            Ehcache adaptedCache = (Ehcache) cache;
            RefreshAheadCache aheadCache = new RefreshAheadCache(adaptedCache, configuration);
            aheadCache.registerCacheLoader(cacheLoaderFactory.createCacheLoader(adaptedCache, new Properties()));
            return aheadCache;
        }

        @Bean
        public AtomicInteger counter() {
            return new AtomicInteger();
        }

        @Bean
        public CacheLoader cacheLoader(final AtomicInteger loadCounter) {
            // see http://ehcache.org/xref-test/net/sf/ehcache/loader/CountingCacheLoader.html
            return new CacheLoader() {
                private final Logger logger = LoggerFactory.getLogger("CacheLoader");

                @Override
                public Object load(Object key) throws CacheException {
                    logger.info("refreshing cache for key " + key);
                    long start = System.currentTimeMillis();
                    try {
                        TimeUnit.MILLISECONDS.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    long end = System.currentTimeMillis();
                    long duration = end - start;

                    logger.info("finished refreshing (duration " + TimeUnit.MILLISECONDS.convert(duration, TimeUnit.MILLISECONDS) + "ms)");
                    loadCounter.incrementAndGet();
                    return "<html> " + key;
                }

                @Override
                public Map loadAll(Collection keys) {
                    Map map = new HashMap(keys.size());
                    for (Object key : keys) {
                        map.put(key, load(key));
                    }
                    return map;
                }

                @Override
                public Object load(Object key, Object argument) {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                @Override
                public Map loadAll(Collection keys, Object argument) {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                @Override
                public String getName() {
                    return "TestLoader";
                }

                @Override
                public CacheLoader clone(Ehcache cache) throws CloneNotSupportedException {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                @Override
                public void init() {
                    //nothing required
                }

                @Override
                public void dispose() throws CacheException {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                @Override
                public Status getStatus() {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }
            };
        }

        @Bean
        public CacheLoaderFactory cacheLoaderFactory(final CacheLoader cacheLoader) {
            return new CacheLoaderFactory() {
                @Override
                public CacheLoader createCacheLoader(Ehcache cache, Properties properties) {
                    return cacheLoader;
                }
            };
        }
    }

    @Autowired
    private RefreshAheadCache refreshAheadCache;

    @Autowired
    private AtomicInteger loadCounter;

    @Before
    public void beforeEachTest(){
        loadCounter.set(0);
    }

    @Test
    public void testRegisteredCacheLoader() {
        assertThat(refreshAheadCache.getRegisteredCacheLoaders()).hasSize(1);
    }

    @Test
    public void testRefreshAheadCache() throws InterruptedException {

        int fetchCounter = 0;
        long maxFetchTime =0L;

        for (int i = 0; i < 100; i++) {
            long start = System.currentTimeMillis();

            Element x = refreshAheadCache.get("x");
            if (null == x) {
                log.info("fetch failed");
                refreshAheadCache.load("x");
            } else {
                long end = System.currentTimeMillis();

                long duration = end - start;
                maxFetchTime = Math.max(maxFetchTime,duration);

                log.info("fetched " + x.getHitCount());
                fetchCounter++;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }

        assertThat(loadCounter.get()).isGreaterThan(3);
        assertThat(fetchCounter).isGreaterThan(85);
        assertThat(maxFetchTime).isLessThan(5);

        log.info("fetchCounter " + fetchCounter);
        log.info("loadCounter  " + loadCounter.get());
    }

}
