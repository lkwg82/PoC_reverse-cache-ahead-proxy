package de.lgohlke.proxy.test;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.CacheEntryFactory;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;
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

import java.util.Random;

import static org.fest.assertions.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class CacheSelfPopulatingTest {
    private static Logger log = LoggerFactory.getLogger(CacheSelfPopulatingTest.class);

    @Configuration
    @EnableCaching
    public static class ContextConfig {

        @Bean
        public EhCacheManagerFactoryBean ehCacheManagerFactoryBean() {
            final EhCacheManagerFactoryBean ehCacheManagerFactoryBean = new EhCacheManagerFactoryBean();
            ehCacheManagerFactoryBean.setShared(true);
            ehCacheManagerFactoryBean.setCacheManagerName(new Random().nextLong() + "");
            return ehCacheManagerFactoryBean;
        }

        @Bean
        public CacheManager cacheManager(EhCacheManagerFactoryBean ehCacheManagerFactoryBean) {
            return new EhCacheCacheManager(ehCacheManagerFactoryBean.getObject());
        }

        @Bean
        public EhCacheFactoryBean cacheFactoryBean() {
            EhCacheFactoryBean ehCacheFactoryBean = new EhCacheFactoryBean();
            ehCacheFactoryBean.setCacheName("test");
            ehCacheFactoryBean.setEternal(true);
            ehCacheFactoryBean.setTimeToIdle(0);
            ehCacheFactoryBean.setTimeToLive(0);
            return ehCacheFactoryBean;
        }

        @Bean
        public Ehcache backingCache(EhCacheFactoryBean ehCacheFactoryBean) {
            return ehCacheFactoryBean.getObject();
        }

        @Bean
        public SelfPopulatingCache selfPopulatingCache(EhCacheFactoryBean ehCacheFactoryBean, CacheEntryFactory cacheEntryFactory) {
            Ehcache ehcache = ehCacheFactoryBean.getObject();
            return new SelfPopulatingCache(ehcache, cacheEntryFactory);
        }

        /**
         * the 'client' which gets the entry into the cache
         *
         * @return
         */
        @Bean
        public CacheEntryFactory cacheEntryFactory() {
            return new CacheEntryFactory() {
                @Override
                public Object createEntry(Object key) throws Exception {
                    return new Element(key, "test");
                }
            };
        }
    }

    @Autowired
    private SelfPopulatingCache selfPopulatingCache;

    @Autowired
    private Ehcache backingCache;

    @Test
    public void testSelfLoadingCache() {
        String key = "x";

        assertThat(backingCache.get(key)).isNull();

        Element elementFromSelfpopulatingCache = selfPopulatingCache.get(key);

        assertThat(backingCache.get(key)).isEqualTo(elementFromSelfpopulatingCache);
    }
}
