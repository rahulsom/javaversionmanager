package com.github.rahulsom.jvm

import com.google.appengine.api.memcache.MemcacheService
import com.google.appengine.api.memcache.MemcacheServiceFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import groovy.json.JsonSlurper
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

  private static String getUrl(String url) {
    int tries = 3
    while (tries --) {
      try {
        return new URL(url).get().text
      } catch (Exception ignore) {

      }
    }
    throw new RuntimeException('Could not get text from url')
  }
  private Gson gson = new Gson()

  List<String> getVersionNumbers() {
    getBuilds(false).collect { it.version }.unique()
  }

  Map<String, List<String>> getTags() {
    def tagList = this.builds*.tags.unique()
    [
        type    : tagList.collect { it[0] }.unique(),
        os      : tagList.collect { it[1] }.unique(),
        arch    : tagList.collect { it[2] }.unique(),
        fileType: tagList.collect { it[3] }.unique(),
    ]
  }

  List<String> getTagSets() {
    this.builds.collect { it.tags.join(' ') }.unique()
  }

  List<JavaBuild> getBuilds(boolean reload = false) {
    def memcacheKey = 'allBuilds'
    def expirationTime = 60 * 60 * 48

    if (reload || !theCache.contains(memcacheKey)) {
      log.info "Computing cacheable value"
      def builds = archivedBuilds + currentVersionBuilds + earlyAccessBuilds

      def sortedBuilds = builds.flatten().sort(false, new BuildComparator()).reverse()
      def json = zip(gson.toJson(sortedBuilds))
      theCache.clearAll()
      theCache.put(memcacheKey, json, byDeltaSeconds(expirationTime), SET_ALWAYS)
      log.info "Done computing cacheable value"
    }

    def collectionType = new TypeToken<Collection<JavaBuild>>() {}.type;
    def json = unzip(theCache.get(memcacheKey) as byte[])
    log.info "Extracted from cache"
    gson.fromJson(json, collectionType) as List<JavaBuild>
  }

  List<JavaBuild> getArchivedBuilds() {
    computeArchiveVersions().collectNested { it.versions }.collectNested { it.builds }
  }

  List<JavaBuild> getEarlyAccessBuilds() {
    def document = Jsoup.parse(getUrl(earlyAccessUrl))
    def scriptTag = document.select('script').find { it.block && it.data().contains('getAskLicense') }.data()
    def eaRegex = /document\.getElementById\("(.+)"\)\.href *= *"(http.*)";/
    scriptTag.findAll(eaRegex).
        collect { urlLine ->
          def m = urlLine =~ eaRegex
          def filePath = m[0][2].trim()
          new JavaBuild(key: filePath.split('/')[-1], filePath: filePath, majorVersion: '9', title: '')
        }.
        findAll { it.version != '-1' }
  }

  List<JavaBuild> getCurrentVersionBuilds() {
    def document = Jsoup.parse(getUrl(currentUrl))
    def javaSeLink = document.select('a').find { it.text().trim() == 'Java SE' }.attr('href')
    log.info "Java SE Link: $javaSeLink"
    document = Jsoup.parse(getUrl(computeUrl(new URL(currentUrl), javaSeLink)))
    def downloadLink = document.select('img[alt="Download JDK"]').parents().head().attr('href')
    def m = downloadLink =~ /.*downloads\/jdk(\d+)-downloads.*/
    def majorVersion = m[0][1] as String
    log.info "DL Link: ${computeUrl(new URL(currentUrl), downloadLink)}"
    def releaseVersions = getArchiveVersions(computeUrl(new URL(currentUrl), downloadLink), majorVersion)
    releaseVersions.collectNested { it.builds }
  }

  List<JavaReleaseVersion> getArchiveVersions(String url, String majorVersion) {
    def document = Jsoup.parse(getUrl(url))

    def scripts = document.select('script')*.data().
        findAll { it?.contains('downloads[') }

    scripts.collect { body ->
      def titleLineRegex = /downloads\['(.+)'\]\['title'\] = "(.+)";/
      def titleLineRegex2 = /downloads\['(.+)'\] = \{ ?title: "(.+)", .*\}/
      def clVal = body.findAll(titleLineRegex).collect { titleLine ->
        def m = titleLine =~ titleLineRegex
        // log.info "Found title match: ${m[0][0]}"
        new JavaReleaseVersion(key: m[0][1].trim(), versionTitle: m[0][2].trim())
      }
      if (!clVal) {
        clVal = body.findAll(titleLineRegex2).collect { titleLine ->
          def m = titleLine =~ titleLineRegex2
          // log.info "Found title match: ${m[0][0]}"
          new JavaReleaseVersion(key: m[0][1].trim(), versionTitle: m[0][2].trim())
        }
      }
      log.info "clVal: $clVal"

      def fileLineRegex = /downloads\['(.+)'\]\['files'\]\['(.+)'\] = (\{.*\});/
      body.findAll(fileLineRegex).collect { fileLine ->
        def m = fileLine =~ fileLineRegex
        // log.info "Found file match: ${m[0][0]}"
        def version = clVal.find { it.key == m[0][1] }
        if (version && !(version.key =~ /FJ-KES-..-G-F/) && !(version.key.contains('demo'))) {
          def json = m[0][3]
          def filePathBlob = new JsonSlurper().parseText(json)

          def filePath = filePathBlob.filepath.replace('/otn/', '/otn-pub/')
          def size = filePathBlob.size
          def title = filePathBlob.title
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
        version
      }

      clVal
    }.flatten() as List<JavaReleaseVersion>
  }

  List<JavaMajorVersion> computeArchiveVersions() {
    def document = Jsoup.parse(getUrl(archiveUrl))
    document.select('a').
        findAll { it.text() =~ /Java SE \d.*/ }.
        collect {
          def version = it.text()
          def href = it.attr('href')
          String url = computeUrl(new URL(archiveUrl), href)
          def versions = getArchiveVersions(url, version)
          new JavaMajorVersion(version: version, link: href, versions: versions)
        }
  }

  String computeUrl(URL startingUrl, String href) {
    startingUrl.port > 0 ?
        "${startingUrl.protocol}://${startingUrl.host}:${startingUrl.port}${href}" :
        "${startingUrl.protocol}://${startingUrl.host}${href}"
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
