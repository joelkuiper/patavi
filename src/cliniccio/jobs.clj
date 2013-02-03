(ns cliniccio.jobs
  (:use cliniccio.util)
  (:require [clojure.tools.logging :as log]))

;; Create a job-queue and a map for keeping track of the status
(def job-queue (ref clojure.lang.PersistentQueue/EMPTY))
(def jobs (atom {}))

(defn- dequeue! [queue-ref]
  ;; Pops the first element off the queue-ref
  (dosync 
    (let [item (peek @queue-ref)]
      (alter queue-ref pop)
      item)))

(defn status [uuid] 
  (let [job (get @jobs uuid)] 
    (if (not-nil? job) 
      {:uuid uuid
       :running (realized? job)}
      nil)))

(defn schedule! [task] 
  ;; Schedule a task to be executed, expects a function (task) to be evaluated
  (let [uuid (uuid)
        job (delay task)]
    (dosync 
      (swap! jobs assoc uuid job) 
      (alter job-queue conj job)
      uuid)))

(defn- run-jobs []
  ;; Runs the jobs 
  (while true
    (Thread/sleep 10)
    (let [curr (dequeue! job-queue)] 
      (if-not (nil? curr) 
        (@curr)))))

(defn run []
  (log/debug "Starting scheduler")
  (.start (Thread. run-jobs)))

