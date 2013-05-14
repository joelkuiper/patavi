library(smaa)
library(hitandrun)
library(RJSONIO)

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

harSample <- function(constr, n , N) {
    transform <- simplex.createTransform(n)
    constr <- simplex.createConstraints(transform, constr)
    seedPoint <- createSeedPoint(constr, homogeneous=TRUE)
    har(seedPoint, constr, N=N * (n-1)^3, thin=(n-1)^3, homogeneous=TRUE, transform=transform)$samples
}

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

smaa <- function(params) {
    params <- fromJSON(params)

	N <- 10000
	n <- length(params$criteria)
	m <- length(params$alternatives)
	crit <- names(params$criteria)
	alts <- names(params$alternatives)

	pvf <- lapply(params$criteria, function(criterion) {
		range <- criterion$pvf$range
		if (criterion$pvf$type == 'linear-increasing') {
			return(partialValue(range[1], range[2]))
		} else if (criterion$pvf$type == 'linear-decreasing') {
			return(partialValue(range[2], range[1]))
		} else {
			stop(paste("PVF type '", criterion$pvf$type, "' not supported.", sep=''))
		}
	})

	meas <- array(dim=c(N,m,n), dimnames=list(NULL, alts, crit))
	for (m in params$performanceTable) {
		if (m$performance$type == 'dbeta') {
			meas[, m$alternative, m$criterion] <- pvf[[m$criterion]](rbeta(N, m$performance$parameters['alpha'], m$performance$parameters['beta']))
		} else {
			stop(paste("Performance type '", m$performance$type, "' not supported.", sep=''))
		}
	}

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

	weights <- harSample(constr, n, N)

	utils <- smaa.values(meas, weights)
	ranks <- smaa.ranks(utils)

	wrap.matrix <- function(m) {
	    l <- lapply(rownames(m), function(name) { m[name,] })
	    names(l) <- rownames(m)
	    l
	}
	results <- list(
		results = list("parameters"=params,
					   "ranks"=wrap.matrix(smaa.ra(ranks)),
					   "ranks_plot"="no way!"),
		descriptions = list("Parameters", "Rank acceptabilities", "Rank acceptabilities")
	)

    results <- mapply(wrap.result,
           results$results,
           results$descriptions,
           SIMPLIFY=F)
    toJSON(results)
}
