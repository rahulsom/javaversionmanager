package admin

import com.github.rahulsom.jvm.JavaServiceFacade

def retries = 3
Exception lastException = null
if (retries) {
    try {
        new JavaServiceFacade().getBuilds(true)
    } catch (Exception e) {
        lastException = e
        sleep 10000
        retries --
    }
} else {
    throw lastException
}

println "Done"