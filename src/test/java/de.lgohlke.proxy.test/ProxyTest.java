package de.lgohlke.proxy.test;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spring.javaconfig.SingleRouteCamelConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
// Put here to prevent Spring context caching across tests and test methods since some tests inherit
// from this test and therefore use the same Spring context.  Also because we want to reset the
// Camel context and mock endpoints between test methods automatically.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ProxyTest {
    public static final String DIRECT_WEBSERVER = "direct:webserver";
    public static final String DIRECT_REVERSEPROXY = "direct:reverseProxy";

    @Autowired
    private CamelContext context;

    @EndpointInject(uri = DIRECT_WEBSERVER)
    private Endpoint directToWebserverEP;

    @Produce(uri = DIRECT_WEBSERVER)
    private ProducerTemplate webserverTemplate;

    @EndpointInject(uri = DIRECT_REVERSEPROXY)
    private Endpoint directToReverseproxyEP;

    @Produce(uri = DIRECT_REVERSEPROXY)
    private ProducerTemplate reverseProxyTemplate;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint resultEndpoint;

    @Configuration
    public static class ContextConfig extends SingleRouteCamelConfiguration {

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
                    final String webserver = "jetty:http://localhost:1801";
                    final String reverseProxy = "jetty:http://localhost:1800?matchOnUriPrefix=true";


                    from(DIRECT_REVERSEPROXY).to(reverseProxy);
                    // the actual reverse-proxy
                    from(reverseProxy).to(webserver + "?bridgeEndpoint=true&throwExceptionOnFailure=false");

                    from(DIRECT_WEBSERVER).to(webserver + "?throwExceptionOnFailure=false");
                    from(webserver + "?matchOnUriPrefix=true").process(dummyWebServer()).to("mock:result");
                }
            };
        }

        public Processor dummyWebServer() {
            return new Processor() {

                @Override
                public void process(Exchange exchange) throws Exception {
                    final HttpMessage in = exchange.getIn(HttpMessage.class);
                    HttpServletRequest request = in.getRequest();
                    HttpServletResponse response = in.getResponse();
                    if (request.getRequestURI().startsWith("/notfound")) {
                        in.setBody("not found");
                        response.setStatus(404);
                    } else {
                        in.setBody("ok");
                    }
                }
            };
        }
    }

    @Test
    public void testSimpleWebserver404() throws Exception {

        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodiesReceived("not found");
        resultEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 404);

        final Exchange exchange = directToWebserverEP.createExchange();
        final Message message = exchange.getIn();
        message.setHeader(Exchange.HTTP_METHOD, "GET");
        message.setHeader(Exchange.HTTP_PATH, "/notfound");

        webserverTemplate.send(exchange);

        resultEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test
    public void testSimpleWebserver200() throws Exception {

        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodiesReceived("ok");
        resultEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        webserverTemplate.sendBody("test");

        resultEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test
    public void testReverseProxy404() throws Exception {

        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodiesReceived("not found");
        resultEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 404);

        final Exchange exchange = directToReverseproxyEP.createExchange();
        final Message message = exchange.getIn();
        message.setHeader(Exchange.HTTP_METHOD, "GET");
        message.setHeader(Exchange.HTTP_PATH, "/notfound");

        webserverTemplate.send(exchange);

        resultEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS);
    }

    @Test
    public void testReverseProxy200() throws Exception {

        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodiesReceived("ok");
        resultEndpoint.expectedHeaderReceived(Exchange.HTTP_RESPONSE_CODE, 200);

        reverseProxyTemplate.sendBody("test");

        resultEndpoint.assertIsSatisfied(10, TimeUnit.SECONDS);
    }
}
