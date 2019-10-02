(ns action-service.core
  (:use [org.httpkit.server :only [run-server]])
  (:require
    [ring.middleware.reload :as reload]
    [clojure.data.json :as json]
    [clojure.pprint]
    [compojure.route :as cr]
    [compojure.core :as cc]
    [org.httpkit.client :as http]
    [clojure.data.json :as json]
    [environ.core :refer [env]]
    )
  (:gen-class)
  )

(defonce server (atom nil))

(defn track-station-start-action [state]
  (let [station-id (-> state :tracker :slots :ev_station_id)
        {:keys [status headers body error] :as resp} @(http/get (format "http://eismoinfo.lt/eismoinfo-backend/feature-info/EIA/%s" station-id))
        ]
    (print (json/read-str body :key-fn keyword))))

(defn perceive-data [req1]
  (print req1)
  (let [bd ((slurp (req1 :body)) :key-fn keyword)]
    (print (bd :next_action))
    (case (bd :next_action)
      "track_station_start_action" (track-station-start-action bd))))

(defn get-list [& req]
  ;(print "req params" req)
  (let [{:keys [status headers body error] :as resp} @(http/get "http://eismoinfo.lt/eismoinfo-backend/layer-static-features/EIA?lks=true")]
    (if error
      (println "failed" error)
      ;(clojure.pprint/pprint (:features (first (json/read-str body :key-fn keyword))))
      body
      )))

(cc/defroutes all-routes
              (cc/POST "/rasa-webhook" [req] perceive-data)
              (cc/GET "/ev" [] get-list))





(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))


(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 8087))]
    (println "starting on port:" port)

    ; (reset! airport-data (air/load-flights))
    ;; The #' is useful when you want to hot-reload code
    ;; You may want to take a look: https://github.com/clojure/tools.namespace
    ;; and http://http-kit.org/migration.html#reload

    (let [handler (reload/wrap-reload #'all-routes)]
      ;(run-server handler {:port port})
      (reset! server (run-server handler {:port port}))
      )))


