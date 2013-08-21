exec <- function(method, params) {
  results <- if(!is.null(params) && isValidJSON(params, asText=TRUE)) {
    params <- fromJSON(params)
    result <- do.call(method, list(params))
  } else {
    stop("Provided JSON was invalid")
  }
  toJSON(result)
}

save.plot <- function(plot.fn, name, type="png") {
  mimes <- list("png"="image/png", "jpeg"="image/jpeg", "svg"="image/svg+xml")

  if(!(type %in% names(mimes))) { stop("File format not supported") }

  tmp <- tempfile()
  do.call("Cairo", list(file=tmp, type=type, dpi=90))
  plot.fn()
  dev.off()
  if(type == "svg") { tmp <- paste(tmp, ".svg", sep="") }
  content <- readBin(tmp, 'raw', 1024*1024) #1MB filesize limit
  unlink(tmp)
  file <- list(name=paste(name, type, sep="."),
               content=content,
               mime=mimes[[type]])

  assign("files", append(files, list(file)), envir=parent.env(environment()))
}
