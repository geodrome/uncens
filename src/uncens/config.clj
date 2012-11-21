(ns uncens.config)

(def config (-> (clojure.java.io/resource "config.txt")
                (slurp)
                (read-string)))
