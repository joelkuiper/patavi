;; ## Jobs 
;; 
;; When dealing with long running and CPU intensive tasks such as 
;; Monte Carlo Markov Chain needed for Bayesian inference we place the 
;; function (or lambda) into the job queue. The job queue is implemented using
;; a [ThreadPoolExecutor](http://docs.oracle.com/javase/6/docs/api/java/util/concurrent/ThreadPoolExecutor.html)
;; which processes only one job at a time. The jobs can be queried for status.
;; The previous attempt at solving the problem can be found on 
;; [StackOverflow](http://stackoverflow.com/questions/14673108/asynchronous-job-queue-for-web-service-in-clojure).


(ns clinicico.jobs
  (:use clinicico.config) 
  (:import  org.rosuda.REngine.REngineException 
            [java.util.concurrent LinkedBlockingQueue 
            Callable Executors TimeUnit ThreadPoolExecutor])
  (:require [clojure.tools.logging :as log]
            [clj-time.core :as time]))

(def job-executor (ThreadPoolExecutor.
                    1 1 0 TimeUnit/MILLISECONDS (LinkedBlockingQueue.)))

(def jobs (atom {}))

(defn submit 
  "Add a function (job) to the job queue."
  [func]
  (let [job-id   (str (java.util.UUID/randomUUID))
        callable (reify Callable (call [_] (func)))]
    (swap! jobs assoc job-id {:future (.submit job-executor callable)
                              :created (time/now)})
    job-id))

(defn cancel
  "Cancels a queued job.
   Due to the RConnection it would be hard to cancel running jobs,
   so this is not allowed."
  [id]
  (let [job-future (:future (@jobs id))]
    (if (nil? job-future) 
      (throw (clinicico.ResourceNotFound.))
      (.cancel job-future false))))

(defn- job-url 
  [id] 
  (str api-url "job/" id)) 

(defn- cause 
  [^Exception e] 
  (let [cause (.getCause e)] 
    (if (and (not (nil? e)) (instance? REngineException cause))
      (.getMessage cause) 
      (str e))))

(defn- job-results 
  [id]
  (try
    (let [results (.get (:future (@jobs id)))]
      (-> (merge results)
          (assoc :status "completed")))
    (catch Exception e {:status "failed" :cause (cause e)})))

(defn- with-queued 
  [id]
  (let [job-future (:future (@jobs id))
        queue (seq (.toArray (.getQueue job-executor)))]
    (if (nil? queue)
      {:status "running"}
      (let [pos (.indexOf queue job-future)]
        (if (= pos -1) 
          {:status "running"} 
          {:status "pending" :queuePosition (inc pos)})))))

(defn- with-base-status 
  [id job] 
  (-> {}
      (assoc :job (job-url id))
      (assoc :created (get-in job [:created]))))

(defn status 
  "Returns the status of the job associated with id as a map.
   The status can either be `running`, `pending`, `completed`, `failed` or `canceled`
   When the status is `pending` the map will contain a key for the position in the queue."
  [id] 
  (let [job (@jobs id)
        job-future (:future job)]
    (if-not (nil? job-future) 
      (if (.isDone job-future)
        (if (.isCancelled job-future) 
          (-> (with-base-status id job)
              (assoc :status "canceled"))
          (-> (with-base-status id job)
              (merge (job-results id))))
        (-> (with-base-status id job)
            (merge (with-queued id))))
      (throw (clinicico.ResourceNotFound. (str "Could not find job: " id))))))
