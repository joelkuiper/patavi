(ns cliniccio.jobs
  (:use cliniccio.config) 
  (:import [java.util.concurrent LinkedBlockingQueue 
                                 Callable Executors TimeUnit ThreadPoolExecutor])
  (:require [clojure.tools.logging :as log]))

(def job-executor (ThreadPoolExecutor.
                    1 1 0 TimeUnit/MILLISECONDS (LinkedBlockingQueue.)))

(def jobs (atom {}))

(defn submit [func]
  (let [job-id   (str (java.util.UUID/randomUUID))
        callable (reify Callable (call [_] (func)))]
    (swap! jobs assoc job-id (.submit job-executor callable))
    job-id))

(defn- job-url [id] 
  (str base-url "api/job/" id)) 

(defn- job-results [id]
  (try
    (-> {}
        (merge (.get (@jobs id)))
        (assoc :status "completed"))
    (catch Exception e {:status "failed"})))

(defn- with-queued [id]
  (let [job-future (@jobs id)
        queue (seq (.toArray (.getQueue job-executor)))]
    (if (nil? queue)
      {:status "running"}
      (let [pos (.indexOf queue job-future)]
        (if (= pos -1) 
          {:status "running"} 
          {:status "pending" :queuePosition (inc pos)})))))

(defn status [id] 
  (let [job-future (@jobs id)]
    (if-not (nil? job-future) 
      (if (.isDone job-future)
        (-> {}
            (merge (job-results id))
            (assoc :job (job-url id)))
        (-> {} 
            (assoc :job (job-url id))
            (merge (with-queued id))))
      (throw (cliniccio.ResourceNotFound. (str "Could not find job: " id))))))
