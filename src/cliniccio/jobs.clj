(ns cliniccio.jobs
  (:import [java.util.concurrent Callable Executors]))

(def job-executor
  (Executors/newSingleThreadExecutor))

(def jobs (atom {}))

(defn submit-job [func]
  (let [job-id   (str (java.util.UUID/randomUUID))
        callable (reify Callable (call [_] (func)))]
    (swap! jobs assoc job-id (.submit job-executor callable))
    job-id))

