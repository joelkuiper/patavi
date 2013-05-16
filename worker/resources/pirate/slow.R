slow <- function(params) {
  print(paste("Accepted ", params['id'], " Zzzzz"))
  N <- 100;
  for(i in as.single(1:N)) {
    update(i);
    Sys.sleep(abs(rnorm(1, 0.01, 0.5)))
  }
  print(paste("Woke up ", params['id']))
  params
}
