slow <- function(params) {
  N <- 100;
  x <- abs(rnorm(N, 0.001, 0.05))
  for(i in as.single(1:N)) {
    self.oobSend(i);
    Sys.sleep(x[[i]])
  }

  save.plot(function() hist(x), "duration", type="png")

  params
}
