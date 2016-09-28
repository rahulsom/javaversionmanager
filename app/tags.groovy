import com.github.rahulsom.jvm.JavaServiceFacade
import groovy.json.JsonBuilder

response.setContentType('application/json')

if (memcache.contains('tags')) {
  println memcache.get('tags')
} else {
  def respVal = new JsonBuilder(new JavaServiceFacade().tags).toString()
  memcache.put('tags', respVal)
  println respVal
}
