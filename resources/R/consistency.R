consistency <- function(params)  {
  library(gemtc)
  d <- params$network
  if(!is.null(d$file)) { 
    network <- read.mtc.network(d$file)
  } else {
    d <- d$data
    d <- as.data.frame(t(sapply(d, unlist)))
    treatments <- lapply(params$network$treatments, unlist)
    network <- mtc.network(d, unlist(params$network$description), treatments)
  }
  factor <- if(is.null(params$factor)) 2.5 else params$factor
  n.chain <- if(is.null(params$n.chain)) 4  else params$n.chain
  n.adapt <- if(is.null(params$n.adapt)) 5000  else params$n.adapt
  n.iter <- if(is.null(params$n.iter)) 20000  else params$n.iter
  thin <- if(is.null(params$thin)) 1  else params$thin

  model <- mtc.model(network, "Consistency",  factor, n.chain) 
  run <- mtc.run(model, sampler="YADAS", n.adapt=n.adapt, n.iter=n.iter, thin=thin) 

  wrap.result <- function(result, description) { 
    list(data=result, description=description, type=class(result))
  }
  quantiles <- summary(run)$quantiles 
  psrf <- gelman.diag(run)$psrf 
  rank.prob <- rank.probability(run) 

  wrap.plot <- function(name, plot.fn, description) {
    name <- paste(name, ".png", sep="")
    try(png(name))
    plot.fn() 
    dev.off();
    list(url=paste(getwd(), "/", name, sep=""), name=name, description=description)
  }

  plots <- list(plots = list("forest" = (function() forest(run)),
                             "model" = (function() plot(model)),
                             "network" = (function() plot(network)), 
                             "ranks" = (function() barplot(t(rank.prob), col=rainbow(dim(rank.prob)[[1]]), beside=T, legend.text=paste("Rank", rep(1:dim(rank.prob)[[1]]))))),
                descriptions = list("A forest plot for some baseline",
                                    paste("A graph with the treatments as vertices and the comparisons",
                                          "as edges. The solid arrows represent basic parameters, the dotted",
                                          "arrows represent inconsistency factors and solid lines represent",
                                          "comparisons that are not associated with any parameter but do have",
                                          "direct evidence from trials.", sep=" "),
                                    "The graph for the included evidence",
                                    "Bar plot for the rank probabilities"))

  results <- list(results = list("network" = network$data,
																 "treatments" = network$treatments,
																 "description" = network$description,
                                 "quantiles" = quantiles, 
                                 "psrf" = psrf, 
                                 "ranks" = rank.prob),
                  descriptions = list("The generated network used to create the Consistency model",
																			"Treatments compared in this analysis",
																			"Short description",
                                      "Quantiles for each variable",
                                      paste("The `potential scale reduction factor' is calculated for each",
                                            "variable in ‘x’, together with upper and lower confidence limits.",
                                            "Approximate convergence is diagnosed when the upper limit is close to 1.", sep=" "),

                                      paste("For each MCMC iteration, the treatments are ranked by their effect",
                                            "relative to an arbitrary baseline. A frequency table is",
                                            "constructed from these rankings and normalized by the number of",
                                            "iterations to give the rank probabilities.", sep=" ")))

  list(results=mapply(function(result, description) { wrap.result(result, description)}, results$results, results$descriptions, SIMPLIFY=F),
       images=mapply(function(name, plot.fn, description) { wrap.plot(name, plot.fn, description) }, names(plots$plots), plots$plots, plots$descriptions, SIMPLIFY=F))
}
