(ns net.thegeez.clj-board.service
  (:require [clojure.core.async :as async]
            [clojure.data.json :as json]
            [io.pedestal.log :as log]
            [io.pedestal.http :as http]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [net.cgrand.enlive-html :as enlive]
            [net.thegeez.clj-board.boards :as boards]
            [net.thegeez.clj-board.sessions :as sessions]
            [net.thegeez.clj-board.jwt :as jwt]))

(defn for-html
  "status is a http status code
template-fn: context -> string | seq of strings (from enlive)
noop when request is not for html"
  [status template-fn]
  (interceptor/interceptor
   {:name (keyword "net.thegeez.w3a.html" (str "for-html_" status))
    :leave (fn [context]
             (if (and (.contains ^String (get-in context [:request :headers "accept"] "") "text/html")
                      (not= "text/html" (get-in context [:response :headers "Content-Type"])))
               (cond-> context
                 (= (get-in context [:response :status]) status)
                 (update-in [:response] merge
                            {:headers {"Content-Type" "text/html"}
                             :body (let [body (template-fn context)]
                                     (cond
                                       (string? body)
                                       body
                                       (seq body) ;; for enlive
                                       (apply str body)
                                       :else body))}))
               context))}))

(enlive/deftemplate clj-board-index "templates/index.html"
  [context])

(def index
  (interceptor/interceptor
   {:enter (fn [context]
             (assoc context
                    :response {:status 200
                               :headers {"Content-Type" "text/html"}
                               :body (apply str (clj-board-index context))}))}))

(defroutes
  routes
  [[["/"
     {:get [:index index]}]
    ["/sign-in"
     {:get [:sign-in index]}]
    ["/boards/:board-id"
     {:get [:board index]}]
    ["/boards/:board-id/cards/:card-id"
     {:get [:card index]}]
    ["/api/v1/sessions"
     ^:interceptors [http/transit-json-body]
     {:post [:sign-up-post sessions/create]}]
    ["/api/v1/current-user"
     ^:interceptors [http/transit-json-body
                     jwt/require-user]
     {:get [:current-user sessions/get-user]}]
    ["/api/v1/boards"
     ^:interceptors [http/transit-json-body
                     jwt/require-user]
     {:get [:boards boards/boards]
      :post [:boards-post boards/create]}]
    ]])


(def bootstrap-webjars-resource-path "META-INF/resources/webjars/bootstrap/3.3.4")
(def jquery-webjars-resource-path "META-INF/resources/webjars/jquery/1.11.1")
(def font-awesome-webjars-resource-path "META-INF/resources/webjars/font-awesome/4.6.3")

(def service
  {:env :prod
   ::http/router :linear-search

   ::http/routes routes

   ::http/resource-path "/public"

   ::http/default-interceptors [(middlewares/resource bootstrap-webjars-resource-path)
                                (middlewares/resource jquery-webjars-resource-path)
                                (middlewares/resource font-awesome-webjars-resource-path)]

   ::http/type :jetty
   })
