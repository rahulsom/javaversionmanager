import com.github.rahulsom.jvm.JavaBuild
import com.github.rahulsom.jvm.JavaServiceFacade
import com.opencsv.CSVWriter
import groovy.json.JsonBuilder

def tags = (params.tags as String)?.split(',')?.toList() ?: []
def version = params.version as String

def allBuilds = new JavaServiceFacade().archivedVersions.
    collectNested { it.versions }.
    collectNested { it.builds }.
    flatten() as List<JavaBuild>


def builds = allBuilds.
    findAll { b -> version ? b.key.contains(version) : true }

tags.each { t ->
  log.warning "Filtering ${builds.size()} builds for $t"
  if (t.startsWith('!')) {
    builds = builds.findAll { b -> !b.tags.contains(t[1..-1]) }
  } else {
    builds = builds.findAll { b -> b.tags.contains(t) }
  }
}

switch (headers.Accept) {
  case 'application/json':
    writeJson(builds)
    break
  case 'text/tab-separated-values':
    writeTsv(builds)
    break
  default:
    writeTsv(builds)
    break
}

private void writeJson(List<JavaBuild> versionLinks) {
  response.contentType = 'application/json'
  println new JsonBuilder(versionLinks).toPrettyString()
}

private void writeTsv(List<JavaBuild> versionLinks) {
  response.contentType = 'text/tab-separated-values'
  def writer = new CSVWriter(response.writer, '\t' as char, CSVWriter.NO_QUOTE_CHARACTER)
  writer.writeAll(versionLinks.collect {
    [it.version, it.key, it.title, it.filePath, it.size, it.tags.toList().join(' ')].toArray([] as String[])
  })
  writer.flush()
}
