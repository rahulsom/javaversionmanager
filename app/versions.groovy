import com.github.rahulsom.jvm.JavaServiceFacade
import groovy.json.JsonBuilder

response.setContentType('application/json')

if (memcache.contains('versionNumbers')) {
  println memcache.get('versionNumbers')
} else {
  def respVal = new JsonBuilder(new JavaServiceFacade().versionNumbers).toString()
  memcache.put('versionNumbers', respVal)
  println respVal
}
