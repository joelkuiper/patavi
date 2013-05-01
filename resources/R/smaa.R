library(smaa)
library(hitandrun)

wrap.result <- function(result, description) {
    list(data=result, description=description, type=class(result))
}

make.plot <- function(plot.fn) {
    tmp <- tempfile()
    CairoPNG(tmp)
    plot.fn()
    dev.off()
    r <- readBin(tmp, 'raw', 1024*1024) # 1MB filesize limit
    unlink(tmp)
    img <- list(image=r, mime="image/png", metadata=list(format="png"))
    class(img) <- "image"
    img
}

# Basic example for UI prototyping
init.example <- function() {
	N <- 10000
	n <- 3
	m <- 2

	# "Benefit": proximal DVT risk (p)
	# "Benefit": distal DVT risk (d)
	# "Risk": major bleeding risk (b)

	# Heparin
	m.p0 <- rbeta(n=N,20,116)
	m.d0 <- rbeta(n=N,40,96)
	m.b0 <- rbeta(n=N,1,135)

	# Enoxaparin
	m.p1 <- rbeta(n=N,8,121)
	m.d1 <- rbeta(n=N,32,97)
	m.b1 <- rbeta(n=N,5,124)

	# Measurement scales (worst value first)
	s.p <- c(0.25, 0.0)
	s.d <- c(0.4, 0.15)
	s.b <- c(0.1, 0.0)

	# Partial value functions
	partialValue <- function(worst, best) { 
		if (best > worst) {
			function(x) {
				(x - worst) / (best - worst)
			}
		} else {
			function(x) {
				(worst - x) / (worst - best)
			}
		}
	}
	u.p <- partialValue(s.p[1], s.p[2])
	u.d <- partialValue(s.d[1], s.d[2])
	u.b <- partialValue(s.b[1], s.b[2])

	meas <- array(c(
		u.p(m.p0), u.p(m.p1),
		u.d(m.d0), u.d(m.d1),
		u.b(m.b0), u.b(m.b1)), dim=c(N,m,n))

	alts <- c("Heparin", "Enoxaparin")
	crit <- c("Prox DVT", "Dist DVT", "Bleed")
	dimnames(meas) <- list(NULL, alts, crit)

	list(N=N, n=n, m=m, meas=meas, alts=alts, crit=crit)
}

harSample <- function(constr, n , N) {
    transform <- simplex.createTransform(n)
    constr <- simplex.createConstraints(transform, constr)
    seedPoint <- createSeedPoint(constr, homogeneous=TRUE)
    har(seedPoint, constr, N=N * (n-1)^3, thin=(n-1)^3, homogeneous=TRUE, transform=transform)$samples
}

smaa <- function(params) { 
	input <- init.example()
	attach(input)

	# parse preference information
	constr <- do.call(mergeConstraints, lapply(params$preferences,
		function(statement) {
			i1 <- which(crit == statement$criteria[1])
			i2 <- which(crit == statement$criteria[2])
			if (statement$type == "ordinal") {
				ordinalConstraint(n, i1, i2)
			} else if (statement['type'] == "ratio bound") {
				l <- statement$bounds[1]
				u <- statement$bounds[2]
				mergeConstraints(
					lowerRatioConstraint(n, i1, i2, l),
					upperRatioConstraint(n, i1, i2, u)
				)
			}
		})
	)

	print(constr)

	weights <- harSample(constr, n, N)

	utils <- smaa.values(meas, weights)
	ranks <- smaa.ranks(utils)

	results <- list(
		results = list("preferences"=params,
					   "ranks_plot"=make.plot(function() plot(ranks))),
		descriptions = list("Preferences", "Rank acceptabilities")
	)
	
    mapply(wrap.result,
           results$results,
           results$descriptions,
           SIMPLIFY=F)
}
