exec <- function(method, id, params) {
  update <- function(message) {
    file <- paste(id, ".tmp", sep="")
    cat(message, file=file, sep="\n", append=TRUE)
  }
  assign("update", update, envir=parent.env(environment()))

  params <- fromJSON(params)
  result <- do.call(method, list(params))
  unlink(paste(id, ".tmp", sep=""))
  toJSON(result)
}
