import com.github.rahulsom.jvm.JavaServiceFacade
import groovy.json.JsonBuilder

def builds = new JavaServiceFacade().currentVersionBuilds
response.writer.print(new JsonBuilder(builds).toPrettyString())