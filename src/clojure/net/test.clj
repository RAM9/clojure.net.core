(ns clojure.net.test
  (:use [lamina.core]
        [clojure.net.kernel]
        [clojure.net.napply]))

(defn run-test []
  (def kernel1! (new-kernel! (new-ninfo "localhost" 6661)))
  (def kernel2! (new-kernel! (new-ninfo "localhost" 6662)))
  (def kernel3! (new-kernel! (new-ninfo "localhost" 6663)))

  (start-kernel kernel1!)
  (start-kernel kernel2!)
  (await-for timeout kernel1! kernel2!)

  (let [node1-2 (new-node kernel1! (new-ninfo "localhost" 6662))
        node2-1 (new-node kernel2! (new-ninfo "localhost" 6661))
        conn1 @(connect kernel1! (new-ninfo "localhost" 6662))
        conn2 @(connect kernel2! (new-ninfo "localhost" 6661))]
    (. Thread (sleep 2000))
    (let [result (napply
                   node1-2
                   (fn [x y] (+ x y))
                   23 34)]
      (run-pipeline
        result
        #(println "result is" %))))
  )

  (defn -main [& args]
    (run-test)
    "Running tests")
