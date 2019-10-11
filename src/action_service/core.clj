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

(def plugs {:LRA-names ["AC (Mode 3, Type 2)" "CHAdeMO" "CCS (Combo 2)"]
            :short-names ["Type2" "CHAdeMO", "CCS"]}
  )

(defn printout2 [v]
  (println v)
  v)

(defn in? [elm coll]
  (some #(= elm %) coll))

(defonce server (atom nil))

(defn available-to-string [coll val sep]
  (let [avail-coll (filter #(= val (% :value)) coll)]
    (if (= (count avail-coll) (count coll))
      "All"
      (clojure.string/join sep (map #(% :key) avail-coll)) )
     )
  )

(comment
  )

(defn track-station-start-action [state]
  (let [station-id (-> state :tracker :slots :ev_station_id)
        {:keys [status headers body error] :as resp} @(http/get (format "http://eismoinfo.lt/eismoinfo-backend/feature-info/EIA/%s" station-id))
        station-plugs (filter #(in? (% :key) (plugs :LRA-names))
                        (-> (json/read-str body :key-fn keyword) :info first :keyValue) )
        remind-minutes 2
        ]
    (printout2 {
                :status  (if error 500 200)
                :headers {"Content-Type" "application/json"}
                :body    (json/write-str {:events    [      ]
                                          :responses [{:text (str "station " station-id " has plugs : " (clojure.string/join "," (map #(% :key) station-plugs)) " available: " (available-to-string station-plugs "Available" ",") " Starting monitoring .... I'll get back in " remind-minutes " minutes")}
                                                      ]})

                })))

(comment
  {:event                "reminder"
   :action          "action_reminder"
   :date_time    (.format (.plusMinutes (.withNano (java.time.LocalDateTime/now) 0) remind-minutes) (java.time.format.DateTimeFormatter/ISO_LOCAL_DATE_TIME))
   ;  :name             (str "track_reminder" station-id)
   :kill_on_user_msg false
   }
  )

(defn check-station-action [state]
  (let [station-id (-> state :tracker :slots :ev_station_id)
        {:keys [status headers body error] :as resp} @(http/get (format "http://eismoinfo.lt/eismoinfo-backend/feature-info/EIA/%s" station-id))
        station-plugs (filter #(in? (% :key) (plugs :LRA-names))
                              (-> (json/read-str body :key-fn keyword) :info first :keyValue) )

        ]
    (printout2 {
                :status  (if error 500 200)
                :headers {"Content-Type" "application/json"}
                :body    (json/write-str  {:events [] :responses [
                                                                  {:text (str "station " station-id " has plugs : "  (clojure.string/join "," (map #(% :key) station-plugs)) " available: " (available-to-string station-plugs "Available" ","))}
                                                                  ]})

                })))

(defn perceive-data [req1]
  ;(println req1)
  (let [bd (json/read-str (slurp (req1 :body)) :key-fn keyword)]
    (println bd)
    (case (bd :next_action)
      "track_station_start_action" (track-station-start-action bd)
      "check_station_action" (check-station-action bd))))

(defn get-list [& req]
  ;(print "req params" req)
  (let [{:keys [status headers body error] :as resp} @(http/get "http://eismoinfo.lt/eismoinfo-backend/layer-static-features/EIA?lks=true")]
    (if error (println "failed" error))
    {
     :status (if error 500 200)
     :headers {"Context-Type" "application/json"}
     :body body
     }
    ))



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



(def data1
  {:name "Joniškio elektromobilių įkrovos stotelė, A12,",
   :info [{:keyValue [{:key "Photo date", :value "2019-10-02 21:41"}
                      {:key "Road", :value "A12"}
                      {:key "Kilometer", :value "20.31"}
                      {:key "Collection date", :value "2019-10-02 21:46"}
                      {:key "Common status", :value "Available"}
                      {:key "AC (Mode 3, Type 2)", :value "Available"}
                      {:key "CHAdeMO", :value "Available"}
                      {:key "CCS (Combo 2)", :value "Available"}],
           :text "",
           :photos ["http://www.eismoinfo.lt/eismoinfo-backend/image-provider/camera/old?id=87950860"]
           }]})