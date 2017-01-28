import com.github.rahulsom.jvm.JavaBuild
import com.github.rahulsom.jvm.JavaServiceFacade
import com.opencsv.CSVWriter
import groovy.json.JsonBuilder

import static com.google.appengine.api.memcache.Expiration.byDeltaSeconds
import static com.google.appengine.api.memcache.MemcacheService.SetPolicy.SET_ALWAYS

List<String> tags = (params.tags as String)?.split(',')?.toList() ?: []
String version = params.version as String
def format = headers.Accept ?: 'text/tab-separated-values'
if (format == '*/*') {
  format = 'text/tab-separated-values'
}

if (!(format.contains('text/tab-separated-values') || format.contains('application/json'))) {
  response.sendError(400, 'Bad Request: Invalid content type requested')
} else {
  def memcacheKey = "${format}+${tags.toSorted().join(',')}+${version ?: 'all'}"
  if (memcache.contains(memcacheKey)) {
    response.setHeader('X-CacheKey', memcacheKey)
    response.setHeader('X-CacheStatus', 'Hit')
    println memcache.get(memcacheKey)
  } else {

    List<JavaBuild> allBuilds = new JavaServiceFacade().builds

    List<JavaBuild> builds = allBuilds.
        findAll { b -> version ? b.version == version : true }

    tags.each { t ->
      log.info "Filtering ${builds.size()} builds for $t"
      builds = t.startsWith('!') ?
          builds.findAll { b -> !b.tags.contains(t[1..-1]) } :
          builds.findAll { b -> b.tags.contains(t) }
    }
    log.info "Result is ${builds.size()} builds ${memcacheKey}"

    String resp
    switch (format) {
      case ~/.*text\/tab-separated-values.*/:
        response.contentType = 'text/tab-separated-values'
        resp = writeTsv(builds)
        break
      case ~/.*application\/json.*/:
        response.contentType = 'application/json'
        resp = writeJson(builds)
        break
      default:
        response.contentType = 'text/tab-separated-values'
        resp = writeTsv(builds)
        break
    }

    memcache.put(memcacheKey, resp, byDeltaSeconds(60 * 60 * 24), SET_ALWAYS)
    response.setHeader('X-CacheKey', memcacheKey)
    response.setHeader('X-CacheStatus', 'Miss')
    println resp
  }

}

private String writeJson(List<JavaBuild> versionLinks) {
  new JsonBuilder(versionLinks).toString()
}

private String writeTsv(List<JavaBuild> versionLinks) {
  def sw = new StringWriter()
  def writer = new CSVWriter(sw, '\t' as char, CSVWriter.NO_QUOTE_CHARACTER)
  writer.writeAll(versionLinks.collect {
    [it.version, it.key, it.title, it.filePath, it.size, it.tags.toList().join(' ')].toArray([] as String[])
  })
  writer.flush()
  writer.close()
  sw.toString()
}