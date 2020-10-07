(ns action-service.eagent
  (:require  [org.httpkit.client :as http]
             [clojure.data.json :as json]))

(defn call-rasa [text]
  {:keys [status headers body error] :as resp}
  @(http/post "http://localhost:5005/model/parse" {:headers {"Content-Type" "application/json"}
               :body    (json/write-str {:text "hello"})})
  )
