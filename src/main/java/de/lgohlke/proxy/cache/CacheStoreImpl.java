package de.lgohlke.proxy.cache;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CacheStoreImpl implements CacheStore {
    private final Logger logger = LoggerFactory.getLogger(CacheStoreImpl.class);

    private List<Element> elements = new ArrayList<>();

    public void load(Ehcache ehcache) {
        logger.info("+++++ loading +++++");
        for (Element e : elements) {
            ehcache.put(e);
        }
    }

    public void save(Ehcache ehcache) {
        logger.info("+++++ saving +++++");
        for (Object key : ehcache.getKeys()) {
            elements.add(ehcache.get(key));
        }
    }

    public List<Element> getElements() {
        return elements;
    }

    @Override
    public void clear() {
        elements.clear();
    }
}
