package com.github.rahulsom.jvm.utils

import com.google.appengine.api.memcache.MemcacheService
import com.google.appengine.api.memcache.MemcacheServiceFactory
import com.google.appengine.repackaged.com.google.common.base.Supplier

import static com.google.appengine.api.memcache.Expiration.byDeltaSeconds
import static com.google.appengine.api.memcache.MemcacheService.SetPolicy.SET_ALWAYS

/**
 * Helps work with the Cache
 *
 * @author Rahul Somasunderam
 */
class CacheUtils {
  static MemcacheService theCache = MemcacheServiceFactory.memcacheService

  static <T> T getOrCompute(String key, int seconds, Supplier<T> closure) {
    T retval = null
    try {
      retval = theCache.get(key) as T
    } catch (ignore) {
      // We can recompute the value
    }
    if (!retval) {
      retval = closure.get()
      theCache.put(key, retval, byDeltaSeconds(seconds), SET_ALWAYS)
    }
    retval
  }

}
