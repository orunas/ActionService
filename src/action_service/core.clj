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
    [overtone.at-at :as at]
    )
  (:gen-class)
  )

(def res-request-slot { :events
                           [{:event "form" , :name "station_form", :timestamp nil}
                            {:event "slot" , :name "requested_slot", :timestamp nil, :value "ev_station_id"}
                            ]
                :responses [
                            {:buttons [], :custom {}, :template  "utter_ask_ev_station_id", :elements [], :image nil, :attachment nil,  :text nil,  :requested_slot nil,

                             :last_ev_station_status_response_txt nil,
                             :ev_station_id nil
                             }]
                })

(def plugs {:LRA-names   ["AC (Mode 3, Type 2)" "CHAdeMO" "CCS (Combo 2)"]
            :short-names ["Type2" "CHAdeMO", "CCS"]}
  )
(def my-pool (at/mk-pool))

(defn printout2 [v]
  (pprint v)
  v)

(defn in? [elm coll]
  (some #(= elm %) coll))

(defonce shared-val-1 (atom nil))
(defonce shared-val-2 (atom nil))

(defonce server (atom nil))

(defn available-to-string [coll val sep]
  (let [avail-coll (filter #(= val (% :value)) coll)]
    (if (= (count avail-coll) (count coll))
      "All"
      (clojure.string/join sep (map #(% :key) avail-coll)))
    )
  )

(comment
  )

(defn wrap-result-to-json [resp error]
  {
   :status  (if error 500 200)
   :headers {"Content-Type" "application/json"}
   :body    (json/write-str resp)
   }
  )

(defn check-station-action-wih-callback [state callback-url]
  (println "in check-station-action-wih-callback" callback-url)
  (try (let [station-id (-> state :tracker :slots :ev_station_id)
         {:keys [status headers body error] :as resp} @(http/get (format "http://eismoinfo.lt/eismoinfo-backend/feature-info/EIA/%s" station-id))
         station-plugs (filter #(in? (% :key) (plugs :LRA-names))
                               (-> (json/read-str body :key-fn keyword) :info first :keyValue))]
         (let [resp2 @(http/post callback-url {:headers {"Content-Type" "application/json"}
                                        :body    (json/write-str {:name "utter_station_status"})})]
           ;(println "respose" resp2)
           ))
       (catch clojure.lang.ExceptionInfo e
         (println "caught exception" ))
       (finally (println "finally"))))

;panasu kad postinama atgal per callback'a http://localhost:5005/webhooks/rest/webhook?stream=true&token=
(defn track-station-start-action [state]
  (println "track-station-start-action: started with state" state)
  (let [station-id (-> state :tracker :slots :ev_station_id)
        {:keys [status headers body error] :as resp} @(http/get (format "http://eismoinfo.lt/eismoinfo-backend/feature-info/EIA/%s" station-id))
        station-plugs (filter #(in? (% :key) (plugs :LRA-names))
                              (-> (json/read-str body :key-fn keyword) :info first :keyValue))
        remind-secs 30
        conversation-id (-> state :tracker :conversation_id)
        schedule (at/every 10000
                           ;(println (format "http://localhost:5005/conversations/%s/execute" conversation-id))
                           #(check-station-action-wih-callback state (format "http://localhost:5005/conversations/%s/execute?output_channel=callback" "default"))
                           my-pool)
        ]
    (println (format "http://localhost:5005/conversations/%s/execute" conversation-id))
    (printout2 {
                :status  (if error 500 200)
                :headers {"Content-Type" "application/json"}
                :body    (json/write-str {:events    []
                                          :responses [{:text (str "ID:" station-id " "  "has plugs : " (clojure.string/join "," (map #(% :key) station-plugs)) " available: " (available-to-string station-plugs "Available" ",") " Starting monitoring .... I'll get back in " remind-secs " seconds")}
                                                      ]})})))

(comment
  {:event            "reminder"
   :action           "action_reminder"
   :date_time        (.format (.plusMinutes (.withNano (java.time.LocalDateTime/now) 0) remind-minutes) (java.time.format.DateTimeFormatter/ISO_LOCAL_DATE_TIME))
   ;  :name             (str "track_reminder" station-id)
   :kill_on_user_msg false
   }
  )



(defn get-station [station-id]
  (println station-id)
  (let [{:keys [status headers body error] :as resp} @(http/get (format "http://eismoinfo.lt/eismoinfo-backend/feature-info/EIA/%s" station-id))
        data (json/read-str body :key-fn keyword)
        station-plugs (filter #(in? (% :key) (plugs :LRA-names))
                              (-> data :info first :keyValue))]

    (println station-plugs)
    (wrap-result-to-json (->  data :info first :keyValue) error)
    )
  )

(defn check-station-action-data [state]
  (println state)
  (let [station-id (-> state :tracker :slots :ev_station_id)
        {:keys [status headers body error] :as resp} @(http/get (format "http://eismoinfo.lt/eismoinfo-backend/feature-info/EIA/%s" station-id))
        data (json/read-str body :key-fn keyword)
        station-plugs (filter #(in? (% :key) (plugs :LRA-names))
                              (-> data :info first :keyValue)) ]

                {:events [] :responses [{:text (str "ID:(" station-id ") \"" (data :name) "\" has plugs : " (clojure.string/join "," (map #(% :key) station-plugs)) " available: " (available-to-string station-plugs "Available" ","))}
                                                                 ]}))

(defn check-station-action [state]
  (println "check-station-action")
  (let [station-id (-> state :tracker :slots :ev_station_id)
        {:keys [status headers body error] :as resp} @(http/get (format "http://eismoinfo.lt/eismoinfo-backend/feature-info/EIA/%s" station-id))
        station-plugs (filter #(in? (% :key) (plugs :LRA-names))
                              (-> (json/read-str body :key-fn keyword) :info first :keyValue)) ]
    {:events [] :responses [
                            {:text (str "station " station-id " has plugs : " (clojure.string/join "," (map #(% :key) station-plugs)) " available: " (available-to-string station-plugs "Available" ","))}
                            ]}))


(defn form-action [bd]
  (let [ev-station-id (-> bd :tracker :slots :ev_station_id )]
    (println "form-action:" ev-station-id)
    (printout2
      (if ev-station-id
        (check-station-action-data bd)
        res-request-slot))
    )
  )


(defn perceive-data [req]
  (let [bd-str  (slurp (req :body))
        bd (json/read-str bd-str :key-fn keyword)]
    (println bd-str)
    (case (bd :next_action)
      "track_station_start_action" (track-station-start-action bd)
      "check_station_action" (wrap-result-to-json (check-station-action bd) nil)
      "station_form" (wrap-result-to-json (form-action bd) nil))
    ;    "station_form"
    ))

(defn get-list [& req]
  ;(print "req params" req)
  (let [{:keys [status headers body error] :as resp} @(http/get "http://eismoinfo.lt/eismoinfo-backend/layer-static-features/EIA?lks=true")]
    (if error (println "failed" error))
    {
     :status  (if error 500 200)
     :headers {"Content-Type" "application/json"}
     :body    body
     }
    ))
(defn get-cached-val-1 [& req] @shared-val-1)
(defn get-cached-val-2 [& req] @shared-val-2)

(defn get-bot-callback [req]
  (let [bd (json/read-str (slurp (req :body)) :key-fn keyword)]
    (println "bot:" (bd :text))))

(cc/defroutes all-routes
              (cc/POST "/rasa-webhook" [req] perceive-data)
              (cc/GET "/cached-val-1" [] get-cached-val-1)
              (cc/GET "/cached-val-2" [] get-cached-val-2)
              (cc/GET "/ev" [] get-list)
              (cc/POST "/callback" [req] get-bot-callback)
              (cc/GET "/station/:id" [id] (get-station id)))





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
           :text     "",
           :photos   ["http://www.eismoinfo.lt/eismoinfo-backend/image-provider/camera/old?id=87950860"]
           }]})



(def res-data1 { :events
                [{:event "form" , :name "test_form", :timestamp nil}
                 ;{:event "slot" , :name "ev_station_id", :timestamp nil}
                 {:event "form" , :name "requested_slot", :timestamp nil, :value "ev_station_id"}
                 ]
                :responses [
                            {:buttons [],
                             :custom {},
                             :template  "utter_ask_ev_station_id",
                             :elements [],
                             :image nil,
                             :attachment nil,
                             :text nil,
                             :requested_slot nil,

                             :ev_station nil,
                             :road_number nil,
                             :last_ev_station_status_response_txt nil,
                             :ev_station_id nil
                             }]
                })