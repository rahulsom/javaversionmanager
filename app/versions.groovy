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

if (!(format in ['text/tab-separated-values', 'application/json'])) {
  response.sendError(400, 'Bad Request: Invalid content type requested')
} else {
  def memcacheKey = "${format}+${tags.toSorted().join(',')}+${version ?: 'all'}"
  if (memcache.contains(memcacheKey)) {
    println memcache.get(memcacheKey)
  } else {

    List<JavaBuild> allBuilds = new JavaServiceFacade().archivedVersions.
        collectNested { it.versions }.
        collectNested { it.builds }.
        flatten() as List<JavaBuild>

    List<JavaBuild> builds = allBuilds.
        findAll { b -> version ? b.key.contains(version) : true }

    tags.each { t ->
      log.warning "Filtering ${builds.size()} builds for $t"
      if (t.startsWith('!')) {
        builds = builds.findAll { b -> !b.tags.contains(t[1..-1]) }
      } else {
        builds = builds.findAll { b -> b.tags.contains(t) }
      }
    }

    String resp
    switch (format) {
      case 'application/json':
        response.contentType = 'application/json'
        resp = writeJson(builds)
        break
      default:
        response.contentType = 'text/tab-separated-values'
        resp = writeTsv(builds)
        break
    }

    memcache.put(memcacheKey, resp, byDeltaSeconds(60* 60 * 24), SET_ALWAYS)
    println resp
  }

}

private String writeJson(List<JavaBuild> versionLinks) {
  new JsonBuilder(versionLinks).toPrettyString()
}

private String writeTsv(List<JavaBuild> versionLinks) {
  def sw = new StringWriter()
  def writer = new CSVWriter(sw, '\t' as char, CSVWriter.NO_QUOTE_CHARACTER)
  writer.writeAll(versionLinks.collect {
    [it.version, it.key, it.title, it.filePath, it.size, it.tags.toList().join(' ')].toArray([] as String[])
  })
  writer.flush()
  writer.close()
  writer.toString()
}