import com.github.rahulsom.jvm.JavaServiceFacade
import groovy.json.JsonBuilder

response.setContentType('application/json')

if (params.sets == '1') {
  if (memcache.contains('tagsets')) {
    println memcache.get('tagsets')
  } else {
    def respVal = new JsonBuilder(new JavaServiceFacade().tagSets).toString()
    memcache.put('tagsets', respVal)
    println respVal
  }
} else {
  if (memcache.contains('tags')) {
    println memcache.get('tags')
  } else {
    def respVal = new JsonBuilder(new JavaServiceFacade().tags).toString()
    memcache.put('tags', respVal)
    println respVal
  }
}
