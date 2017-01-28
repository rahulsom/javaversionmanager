get '/',                        forward: '/index.html'

get '/builds',                  forward: '/builds.groovy'
get '/builds/t/@tags',          forward: '/builds.groovy?tags=@tags'
get '/builds/t/@tags/@version', forward: '/builds.groovy?tags=@tags&version=@version'
get '/builds/v/@version',       forward: '/builds.groovy?version=@version'

get '/current',                 forward: '/current.groovy'

get '/versions',                forward: '/versions.groovy'

get '/tags',                    forward: '/tags.groovy'

get '/admin/reload',            forward: '/admin/reload.groovy'