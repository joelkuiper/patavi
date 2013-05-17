exec <- function(method, id, params) {
  update <- function(message) {
    file <- paste(id, ".tmp", sep="")
    cat(message, file=file, sep="\n", append=TRUE)
  }
  assign("update", update, envir=parent.env(environment()))

  if(!is.null(params) && isValidJSON(params, asText=T)) {
    params <- fromJSON(params)
    result <- do.call(method, list(params))
    unlink(paste(id, ".tmp", sep=""))
    toJSON(result)
  } else {
    unlink(paste(id, ".tmp", sep=""))
    stop("Provided JSON was invalid")
  }
}

save.plot <- function(plot.fn, filename, type="PNG") {
  mimes <- list("PNG"="image/png", "SVG"="image/svg+xml")
  if(!(type %in% names(mimes))) { stop("File format not supported") }

  tmp <- tempfile()
  do.call(paste("Cairo", type, sep=""), list(tmp))
  plot.fn()
  dev.off()
  content <- readBin(tmp, 'raw', 1024*1024) #1MB filesize limit
  unlink(tmp)
  file <- list(name=paste(filename, tolower(type), sep="."),
               content=content,
               mime=mimes[[type]])
  assign("files", c(files, file), envir=parent.env(environment()))
}


