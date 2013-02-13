wrap.result <- function(result, description) { 
  list(data=result, description=description, type=class(result))
}

network <- function(params)  {
  library(gemtc)
  d <- params$network
  print(d)
  if(!is.null(d$file)) { 
    network <- read.mtc.network(d$file)
    filename <- strsplit(d$file, "\\.")[[1]][[1]]
  } else {
    stop("No GeMTC file found");
  }

  results <- list(results      = list("title" = filename,
                                      "data" = network$data,
                                      "type" = if (is.null(network$data$mean)) "dichotomous" else "continuous", 
                                      "treatments" = network$treatments,
                                      "description" = network$description),
                  descriptions = list("The generated network used to create the Consistency model",
                                      "Treatments compared in this analysis",
                                      "Short description"))

  list(images=list(),
       results=mapply(function(result, description) { 
                      wrap.result(result, description) }, 
                      results$results,
                      results$descriptions, 
                      SIMPLIFY=F))
}
