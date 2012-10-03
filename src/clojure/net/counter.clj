(ns clojure.net.counter
  (:use [clojure.net.kernel]
        [clojure.net.plugin]
        [clojure.tools.logging :only (debug info warn)]))

(defn cnt-ensure [counterable cname]
  (let [[counterable counter] (pget counterable :counter cname)]
    (if counter
      [counterable counter]
      [(pset counterable :counter cname 0) 0])))

(defn cnt-inc [counterable cname]
  (let [[counterable counter] (cnt-ensure counterable cname)
        new-counter (+ counter 1)]
    [(pset counterable :counter cname new-counter) new-counter counter]))
