exec <- function(method, params) {
  params <- fromJSON(params)
  result <- do.call(method, list(params))
  toJSON(result)
}
