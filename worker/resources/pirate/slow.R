slow <- function(params) {
  N <- 100;
  x <- abs(rnorm(N, 0.001, 0.05))
  print(paste("printing from slow"))
  for(i in as.single(1:N)) {
    self.oobSend(list(progress=i));
    Sys.sleep(x[[i]])
  }

  save.plot(function() hist(x), "duration", type="png")

  params
}
