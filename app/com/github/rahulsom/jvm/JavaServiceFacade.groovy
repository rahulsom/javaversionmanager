package com.github.rahulsom.jvm

import com.github.rahulsom.jvm.utils.CacheUtils
import com.google.appengine.api.memcache.MemcacheService
import com.google.appengine.api.memcache.MemcacheServiceFactory
import groovy.util.logging.Log
import org.jsoup.Jsoup

import static com.google.appengine.api.memcache.Expiration.byDeltaSeconds
import static com.google.appengine.api.memcache.MemcacheService.SetPolicy.SET_ALWAYS

/**
 * Downloads lists from a service
 */
@Log
class JavaServiceFacade {
  private static final String archiveUrl = 'http://www.oracle.com/technetwork/java/javase/archive-139210.html'
  private static final String currentUrl = 'http://www.oracle.com/technetwork/indexes/downloads/index.html'
  private static final String earlyAccessUrl = 'https://jdk9.java.net/download/'

  MemcacheService theCache = MemcacheServiceFactory.memcacheService

  Closure<String> getUrl = theCache.memoize { String url ->
    log.warning "Fetching $url"
    new URL(url).get().text
  }

  List<JavaReleaseVersion> getArchiveVersions(String url, String majorVersion) {
    def document = Jsoup.parse(getUrl.call(url))

    def scripts = document.select('script')*.data().
        findAll { it?.contains('downloads[') }

    scripts.collect { body ->
      def titleLineRegex = /downloads\['(.+)'\]\['title'\] = "(.+)";/
      def clVal = body.findAll(titleLineRegex).collect { titleLine ->
        def m = titleLine =~ titleLineRegex
        new JavaReleaseVersion(key: m[0][1].trim(), versionTitle: m[0][2].trim())
      }

      def fileLineRegex = /downloads\['(.+)'\]\['files'\]\['(.+)'\] = \{ *"title": *"(.+)", *"size": *"(.+)", *"filepath": *"(.+)",? *\};/
      body.findAll(fileLineRegex).collect { fileLine ->
        def m = fileLine =~ fileLineRegex
        def version = clVal.find { it.key == m[0][1] }
        if (version) {
          version.builds.add(new JavaBuild(title: m[0][3].trim(), size: m[0][4].trim(), filePath: m[0][5].trim(), key: version.key, majorVersion: majorVersion))
        }
      }

      clVal
    }.flatten() as List<JavaReleaseVersion>
  }

  List<JavaMajorVersion> getArchivedVersions(boolean reload = false) {
    def archiveVersions = 'archiveVersions'
    def expirationTime = 60 * 60 * 24

    if (reload) {
      def versions = computeArchiveVersions()
      CacheUtils.theCache.clearAll()
      CacheUtils.theCache.put(archiveVersions, versions, byDeltaSeconds(expirationTime), SET_ALWAYS)
    }
    CacheUtils.getOrCompute(archiveVersions, expirationTime) { computeArchiveVersions() }
  }

  private List<JavaMajorVersion> computeArchiveVersions() {
    def rootUrl = new URL(archiveUrl)
    def document = Jsoup.parse(getUrl.call(rootUrl.toString()))
    document.select('a').
        findAll { it.text() =~ /Java SE \d.*/ }.
        collect {
          def version = it.text()
          def href = it.attr('href')
          def versions = getArchiveVersions(new URL("${rootUrl.protocol}://${rootUrl.host}:${rootUrl.port}${href}").toString(), version)
          new JavaMajorVersion(version: version, link: href, versions: versions)
        }
  }
}