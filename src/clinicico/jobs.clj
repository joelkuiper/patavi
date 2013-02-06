;; ## Jobs 
;; 
;; When dealing with long running and CPU intensive tasks such as 
;; Monte Carlo Markov Chain needed for Bayesian inference we place the 
;; function (or lambda) into the job queue. The job queue is implemented using
;; a [TreadPoolExecutor](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/ThreadPoolExecutor.html)
;; which processes only one job at a time. The jobs can be queried for status.


(ns clinicico.jobs
  (:use clinicico.config) 
  (:import [java.util.concurrent LinkedBlockingQueue 
            Callable Executors TimeUnit ThreadPoolExecutor])
  (:require [clojure.tools.logging :as log]))

(def job-executor (ThreadPoolExecutor.
                    1 1 0 TimeUnit/MILLISECONDS (LinkedBlockingQueue.)))

(def jobs (atom {}))

(defn submit 
  "Add a function (job) to the job queue"
  [func]
  (let [job-id   (str (java.util.UUID/randomUUID))
        callable (reify Callable (call [_] (func)))]
    (swap! jobs assoc job-id (.submit job-executor callable))
    job-id))

(defn- job-url 
  [id] 
  (str base-url "api/job/" id)) 

(defn- job-results 
  [id]
  (try
    (-> {}
        (merge (.get (@jobs id)))
        (assoc :status "completed"))
    (catch Exception e {:status "failed"})))

(defn- with-queued 
  [id]
  (let [job-future (@jobs id)
        queue (seq (.toArray (.getQueue job-executor)))]
    (if (nil? queue)
      {:status "running"}
      (let [pos (.indexOf queue job-future)]
        (if (= pos -1) 
          {:status "running"} 
          {:status "pending" :queuePosition (inc pos)})))))

(defn status 
  "Returns the status of the job associated with id as a map.
   The status can either be `running`, `pending`, `completed`, `failed` or `canceled`
   When the status is `pending` the map will contain a key for the position in the queue"
  [id] 
  (let [job-future (@jobs id)]
    (if-not (nil? job-future) 
      (if (.isDone job-future)
        (-> {}
            (merge (job-results id))
            (assoc :job (job-url id)))
        (-> {} 
            (assoc :job (job-url id))
            (merge (with-queued id))))
      (throw (clinicico.ResourceNotFound. (str "Could not find job: " id))))))
