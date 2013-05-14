(ns clinicico.worker.main
  (:use [clojure.tools.cli :only [cli]])
  (:require [clinicico.worker.task :as tasks :only [initialize]]
            [clojure.tools.logging :as log]))

(defn -main
  [& args]
  (let [[options args banner]
        (cli args
             ["-h" "--help" "Show help" :default false :flag true]
             ["-n" "--nworkers" "The amount of worker threads to start" :default (.availableProcessors (Runtime/getRuntime)) :parse-fn #(Integer. %)]
             ["-m" "--method" "The R method name to execute" :default "echo"])
        method (:method options)]
    (when (or (:help options))
      (println banner)
      (System/exit 0))
    (tasks/initialize method (:nworkers options))
    (while true (Thread/sleep 100))))
