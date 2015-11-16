package org.apache.kylin.storage.cache;

import java.util.List;

import net.sf.ehcache.Element;

import org.apache.kylin.metadata.realization.SQLDigest;
import org.apache.kylin.metadata.realization.StreamSQLDigest;
import org.apache.kylin.metadata.tuple.ITuple;
import org.apache.kylin.metadata.tuple.ITupleIterator;
import org.apache.kylin.metadata.tuple.SimpleTupleIterator;
import org.apache.kylin.metadata.tuple.TeeTupleIterator;
import org.apache.kylin.storage.ICachableStorageQuery;
import org.apache.kylin.storage.StorageContext;
import org.apache.kylin.storage.tuple.TupleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Ranges;

public class CacheFledgedStaticQuery extends AbstractCacheFledgedQuery {
    private static final Logger logger = LoggerFactory.getLogger(CacheFledgedStaticQuery.class);

    public CacheFledgedStaticQuery(ICachableStorageQuery underlyingStorage) {
        super(underlyingStorage);
    }

    @Override
    public ITupleIterator search(final StorageContext context, final SQLDigest sqlDigest, final TupleInfo returnTupleInfo) {

        streamSQLDigest = new StreamSQLDigest(sqlDigest, null);
        StreamSQLResult cachedResult = getStreamSQLResult(streamSQLDigest);
        ITupleIterator ret;

        if (cachedResult != null) {
            logger.info("using existing cache");
            context.setReusedPeriod(Ranges.<Long> all());
            return new SimpleTupleIterator(cachedResult.reuse(Ranges.<Long> all()));
        } else {
            logger.info("no existing cache to use");
            ret = underlyingStorage.search(context, sqlDigest, returnTupleInfo);

            //use another nested ITupleIterator to deal with cache
            final TeeTupleIterator tee = new TeeTupleIterator(ret);
            tee.addCloseListener(this);
            return tee;
        }
    }

    @Override
    public void notify(List<ITuple> duplicated, long createTime) {
        if (needSaveCache(createTime)) {
            StreamSQLResult newCacheEntry = new StreamSQLResult(duplicated, Ranges.<Long> all(), null);
            CACHE_MANAGER.getCache(this.underlyingStorage.getStorageUUID()).put(new Element(streamSQLDigest.hashCode(), newCacheEntry));
            logger.info("cache after the query: " + newCacheEntry);
        }
    }

}
