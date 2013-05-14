slow <- function(params) {
  print(paste("Accepted ", params['id'], " Zzzzz"))
  Sys.sleep(20)
  print(paste("Woke up ", params['id']))
  params
}
