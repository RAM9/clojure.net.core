(ns clojure.net.util
  (:use [clojure.net.kernel]
        [clojure.tools.logging :only (debug info warn)]))

(defn ensure-util [obj]
  (let [cmeta (meta obj)
        util (:util cmeta)]
    (if util
      obj
      (with-meta
        obj
        (assoc
          cmeta
          :util
          {})))))

(defn get-field [obj field]
  (field (:util (meta obj))))

(defn set-field [obj field value]
  (let [obj (ensure-util obj)
        cmeta (meta obj)
        util (:util cmeta)]
    (with-meta
      obj
      (assoc
        cmeta
        :util
        (assoc util field value)))))

(defn create-counter [counterable cname]
  (let [counters (get-field counterable :counters)
        counter (counters cname)]
    (if counter
      counterable
      (set-field
        counterable
        :counters
        (assoc counters cname 0)))))

(defn inc-counter [counterable cname]
  (let [counterable (create-counter counterable cname)
        counters (get-field counterable :counters)
        counter (counters cname)]
    (let [counterable (set-field
                        counterable
                        :counters
                        (assoc counters cname (+ counter 1)))]
      [counterable counter])))
