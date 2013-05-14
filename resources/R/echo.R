echo <- function(params) {
	library(RJSONIO)
	print(params)
	print(toJSON(fromJSON(params)))
}
