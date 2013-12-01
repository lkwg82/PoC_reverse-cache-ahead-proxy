package de.lgohlke.proxy.test;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.CacheEntryFactory;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.apache.camel.util.ExchangeHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.cache.ehcache.EhCacheFactoryBean;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
// Put here to prevent Spring context caching across tests and test methods since some tests inherit
// from this test and therefore use the same Spring context.  Also because we want to reset the
// Camel context and mock endpoints between test methods automatically.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class CamelWithProxyCacheTest {

    private static final String MOCK_RESULT = "mock:result";
    private static final String DIRECT_TEST = "direct:test";

    @Autowired
    private CamelContext context;

    @EndpointInject(uri = MOCK_RESULT)
    private MockEndpoint resultEndpoint;

    @EndpointInject(uri = DIRECT_TEST)
    private Endpoint directEP;


    @Configuration
    @EnableCaching
    public static class ContextConfig extends SingleRouteCamelConfiguration {


        public static final String DIRECT_HTTP_CLIENT = "direct:httpClient";

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
            ehCacheFactoryBean.setCacheName("test2");
            ehCacheFactoryBean.setEternal(true);
            ehCacheFactoryBean.setTimeToIdle(0);
            ehCacheFactoryBean.setTimeToLive(0);
            ehCacheFactoryBean.setDiskPersistent(false);
            ehCacheFactoryBean.setOverflowToDisk(false);
            return ehCacheFactoryBean;
        }

        @Bean
        public Ehcache backingCache(EhCacheFactoryBean ehCacheFactoryBean) {
            return ehCacheFactoryBean.getObject();
        }

        @Bean
        public SelfPopulatingCache selfPopulatingCache(Ehcache backingCache, CacheEntryFactory cacheEntryFactory) {
            return new SelfPopulatingCache(backingCache, cacheEntryFactory);
        }

        @Bean
        public CamelContext camelContext() throws Exception {
            final CamelContext context = super.camelContext();

            context.setUseMDCLogging(true);
            context.setUseBreadcrumb(true);
            return context;
        }

        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {
                public void configure() {
                    final String webserver = "jetty:http://localhost:1800?bridgeEndpoint=true&throwExceptionOnFailure=false&matchOnUriPrefix=true";
                    final String reverseProxyDirect = "jetty:http://localhost:1801?matchOnUriPrefix=true";
                    final String reverseProxyCaching = "jetty:http://localhost:1802?matchOnUriPrefix=true";

                    // the actual reverse-proxy
                    from(reverseProxyDirect).setHeader("X-REVERSE", constant(1)).to(webserver);
                    from(reverseProxyCaching).setHeader("X-Caching", constant(true)).beanRef("cache");

                    from(DIRECT_HTTP_CLIENT).to(webserver);
                    from(webserver).beanRef("webserver");
                }
            };
        }

        @Bean(name = "webserver")
        public Processor webServer() {
            return new Processor() {

                @Override
                public void process(Exchange exchange) throws Exception {
                    final HttpMessage in = exchange.getIn(HttpMessage.class);
                    HttpServletRequest request = in.getRequest();
                    HttpServletResponse response = in.getResponse();

                    boolean overProxy = in.getHeader("X-REVERSE") != null;
                    TimeUnit.SECONDS.sleep(1);

                    if (request.getRequestURI().startsWith("/notfound")) {
                        in.setBody("not found");
                        response.setStatus(404);
                    } else {
                        in.setBody("ok");
                    }

                    in.setHeader("X-REVERSE", overProxy);
                }
            };
        }

        class RequestCacheKey {
            private final String method;
            private final String requestUri;

            RequestCacheKey(String method, String requestUri) {
                this.method = method;
                this.requestUri = requestUri;
            }

            public String toString() {
                return method + "|" + requestUri;
            }

            String getRequestUri() {
                return requestUri;
            }

            String getMethod() {
                return method;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                RequestCacheKey that = (RequestCacheKey) o;

                if (!method.equals(that.method)) return false;
                if (!requestUri.equals(that.requestUri)) return false;

                return true;
            }

            @Override
            public int hashCode() {
                int result = method.hashCode();
                result = 31 * result + requestUri.hashCode();
                return result;
            }
        }

        class ServiceBean implements Processor {

            private final Ehcache ehcache;

            public ServiceBean(Ehcache ehcache) {
                this.ehcache = ehcache;
            }

            @Override
            public void process(Exchange exchange) throws Exception {
                final HttpMessage httpMessage = exchange.getIn(HttpMessage.class);

                final HttpServletRequest request = httpMessage.getRequest();
                final String requestURI = request.getRequestURI();
                final String method = request.getMethod();

                final RequestCacheKey key = new RequestCacheKey(method, requestURI);
                Element element = ehcache.get(key);

                final Exchange objectValue = (Exchange) element.getObjectValue();
                ExchangeHelper.copyResults(exchange, objectValue);

                exchange.getOut().setHeader("X-HITS", element.getHitCount());
                exchange.getOut().setHeader("X-CACHE-KEY", key.toString());
            }
        }

        class HTTPClientBean implements CacheEntryFactory {
            @EndpointInject(uri = DIRECT_HTTP_CLIENT)
            private Endpoint httpEndpoint;

            @Produce(uri = DIRECT_HTTP_CLIENT)
            private ProducerTemplate httpProducer;

            @Override
            public Object createEntry(Object key) throws Exception {
                if (key instanceof RequestCacheKey) {
                    return forwardRequest((RequestCacheKey) key);
                } else {
                    throw new IllegalArgumentException("can not handle non-string key");
                }
            }

            private Exchange forwardRequest(RequestCacheKey requestCacheKey) throws Exception {
                final Exchange exchange = httpEndpoint.createExchange();

                exchange.getIn().setHeader(Exchange.HTTP_URI, requestCacheKey.getRequestUri());
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, requestCacheKey.getMethod());

                httpProducer.send(exchange);
                return exchange;
            }
        }

        @Bean
        HTTPClientBean cacheEntryFactory() {
            return new HTTPClientBean();
        }

        @Bean(name = "cache")
        public ServiceBean serviceBean(SelfPopulatingCache selfPopulatingCache) {
            return new ServiceBean(selfPopulatingCache);
        }
    }

    @Test
    public void testSimple() throws Exception {


//        Exchange exchange = directEP.createExchange();
//        Message message = exchange.getIn();
//        message.setHeader("key", "x");
//        message.setBody("test");
//
//        directEP.createProducer().process(exchange);

        TimeUnit.SECONDS.sleep(120);

    }
}