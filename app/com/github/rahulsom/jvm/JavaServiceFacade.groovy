package com.github.rahulsom.jvm

import com.google.appengine.api.memcache.MemcacheService
import com.google.appengine.api.memcache.MemcacheServiceFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import groovy.util.logging.Log
import org.jsoup.Jsoup

import static com.google.appengine.api.memcache.Expiration.byDeltaSeconds
import static com.google.appengine.api.memcache.MemcacheService.SetPolicy.SET_ALWAYS
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream


/**
 * Downloads lists from a service
 */
@Log
class JavaServiceFacade {
  private static final String archiveUrl = 'http://www.oracle.com/technetwork/java/javase/archive-139210.html'
  private static final String currentUrl = 'http://www.oracle.com/technetwork/indexes/downloads/index.html'
  private static final String earlyAccessUrl = 'https://jdk9.java.net/download/'

  private MemcacheService theCache = MemcacheServiceFactory.memcacheService
  private Closure<String> getUrl = theCache.memoize { String url -> new URL(url).get().text }
  private Gson gson = new Gson()

  List<JavaBuild> getBuilds(boolean reload = false) {
    def memcacheKey = 'archiveVersions'
    def expirationTime = 60 * 60 * 24

    if (reload || !theCache.contains(memcacheKey)) {
      def versions = computeArchiveVersions()*.versions*.builds.flatten()
      def json = zip(gson.toJson(versions))
      theCache.clearAll()
      theCache.put(memcacheKey, json, byDeltaSeconds(expirationTime), SET_ALWAYS)
    }

    def collectionType = new TypeToken<Collection<JavaBuild>>() {}.type;
    def json = unzip(theCache.get(memcacheKey) as byte[])
    gson.fromJson(json, collectionType) as List<JavaBuild>
  }

  private List<JavaReleaseVersion> getArchiveVersions(String url, String majorVersion) {
    def document = Jsoup.parse(getUrl.call(url))

    def scripts = document.select('script')*.data().
        findAll { it?.contains('downloads[') }

    scripts.collect { body ->
      def titleLineRegex = /downloads\['(.+)'\]\['title'\] = "(.+)";/
      def clVal = body.findAll(titleLineRegex).collect { titleLine ->
        def m = titleLine =~ titleLineRegex
        new JavaReleaseVersion(key: m[0][1].trim(), versionTitle: m[0][2].trim())
      }

      def fileLineRegex =
          /downloads\['(.+)'\]\['files'\]\['(.+)'\] = \{ *"title": *"(.+)", *"size": *"(.+)", *"filepath": *"(.+)",? *\};/
      body.findAll(fileLineRegex).collect { fileLine ->
        def m = fileLine =~ fileLineRegex
        def version = clVal.find { it.key == m[0][1] }
        if (version) {
          def filePath = m[0][5].trim() as String
          def size = m[0][4].trim() as String
          def title = m[0][3].trim() as String
          if (!filePath.contains('jre_config') &&
              !filePath.endsWith('.ps') &&
              !filePath.endsWith('.pdf') &&
              !filePath.endsWith('.html') &&
              !filePath.contains('README') &&
              !filePath.contains('docs') &&
              !filePath.contains('-doc') &&
              !title.contains('JDK Packages and Documentation')
          ) {
            version.builds.add(new JavaBuild(title: title, size: size, filePath: filePath,
                key: version.key, majorVersion: majorVersion))
          }
        }
      }

      clVal
    }.flatten() as List<JavaReleaseVersion>
  }

  private List<JavaMajorVersion> computeArchiveVersions() {
    def rootUrl = new URL(archiveUrl)
    def document = Jsoup.parse(getUrl.call(rootUrl.toString()))
    document.select('a').
        findAll { it.text() =~ /Java SE \d.*/ }.
        collect {
          def version = it.text()
          def href = it.attr('href')
          def versions = rootUrl.port > 0 ?
              getArchiveVersions(new URL("${rootUrl.protocol}://${rootUrl.host}:${rootUrl.port}${href}").toString(), version) :
              getArchiveVersions(new URL("${rootUrl.protocol}://${rootUrl.host}${href}").toString(), version)
          new JavaMajorVersion(version: version, link: href, versions: versions)
        }
  }

  private static byte[] zip(String s) {
    def targetStream = new ByteArrayOutputStream()
    def zipStream = new GZIPOutputStream(targetStream)
    zipStream.write(s.getBytes('UTF-8'))
    zipStream.close()
    def zippedBytes = targetStream.toByteArray()
    targetStream.close()
    return zippedBytes
  }

  private static String unzip(byte[] compressed) {
    def inflaterStream = new GZIPInputStream(new ByteArrayInputStream(compressed))
    def uncompressedStr = inflaterStream.getText('UTF-8')
    return uncompressedStr as String
  }

}
