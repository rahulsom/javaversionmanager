get "/",          forward: "/index.groovy",    cache: 24.hours
get '/versions',  forward: '/versions.groovy', cache: 1.hour
get '/reload',    forward: '/reload.groovy'