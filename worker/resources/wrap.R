update <- function(message) {
  fileConn <- file(paste(id, ".tmp", sep=""))
  writeLines(message, fileConn)
  close(fileConn)
}

exec <- function(method, params) {
  params <- fromJSON(params)
  id <- params['id']
  result <- do.call(method, list(params))
  result['id'] <- id
  toJSON(result)
}
