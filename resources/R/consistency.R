mtc.consistency <- function(network, factor = 2.5 , n.chain = 4, n.adapt = 5000, n.iter = 20000, thin = 1)  {
  model <- mtc.model(network, "Consistency",  factor, n.chain) 
  run <- mtc.run(model, sampler="YADAS", n.adapt=n.adapt, n.iter=n.iter, thin=thin) 
  
  wrap.result <- function(result) { 
    list(data=result, type=class(result))
  }
  quantiles <- summary(run)$quantiles 
  psrf <- gelman.diag(run)$psrf 
  rank.prob <- rank.probability(run) 
  
  wrap.plot <- function(plot.fn, name) {
    name <- paste(name, ".png", sep="")
    try(png(name))
    plot.fn() 
    dev.off();
    paste(getwd(), "/", name, sep="")
  }

  plots <- list("forest" = (function() forest(run)),
                "model" = (function() plot(model)),
                "network" = (function() plot(network)), 
                "ranks" = (function() barplot(t(rank.prob), col=rainbow(dim(rank.prob)[[1]]), beside=T, legend.text=paste("Rank", rep(1:dim(rank.prob)[[1]])))))

  results <- list("quantiles" = quantiles, 
                  "psrf" = psrf, 
                  "ranks" = rank.prob)
  
  list(results=lapply(results, function(x) { wrap.result(x) }),
       images=mapply(function(name, plot.fn) { wrap.plot(plot.fn, name) }, names(plots), plots))
}
