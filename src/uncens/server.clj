(ns uncens.server
  (:use [noir.core :only [defpage]])
  (:require [noir.server :as server]
            [uncens.views.main]))

(server/load-views-ns 'uncens.views)

(def handler (server/gen-handler {:mode :dev
                                  :ns 'uncens.server}))

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (server/start port {:mode mode
                        :ns 'uncens.server})))