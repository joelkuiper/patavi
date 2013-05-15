slow <- function(params) {
  print(paste("Accepted ", params['id'], " Zzzzz"))
  update("Friendly neighbour R calling");
  Sys.sleep(5)
  update("It's me again, R!");
  Sys.sleep(5)
  print(paste("Woke up ", params['id']))
  params
}
