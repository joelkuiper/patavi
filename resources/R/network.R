wrap.result <- function(result, description) { 
  list(data=result, description=description, type=class(result))
}

network <- function(params)  {
  library(gemtc)
  d <- params$network
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
                  descriptions = list("The filename used to generate the network",
                                      "The generated network used to create the Consistency model",
                                      "Type of network",
                                      "Treatments compared in this analysis",
                                      "Short description"))

  mapply(wrap.result,
         results$results,
         results$descriptions, 
         SIMPLIFY=F)
}
