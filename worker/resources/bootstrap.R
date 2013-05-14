#AUTO-GENERATED
source('resources/wrap.R')
l = tryCatch(require('RJSONIO'), warning=function(w) w);
        if(is(l, 'warning')) print(l[1])
l = tryCatch(require('Cairo'), warning=function(w) w);
        if(is(l, 'warning')) print(l[1])