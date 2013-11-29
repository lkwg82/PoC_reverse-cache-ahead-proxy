package de.lgohlke.proxy.cache;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

import java.util.List;

public interface CacheStore {
    void load(Ehcache ehcache);

    void save(Ehcache ehcache);

    List<Element> getElements();

    void clear();
}
